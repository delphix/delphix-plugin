/**
 * Copyright (c) 2018 by Delphix. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jenkins.plugins.delphix;
import io.jenkins.plugins.delphix.objects.ActionStatus;
import io.jenkins.plugins.delphix.objects.JobStatus;
import io.jenkins.plugins.delphix.objects.SelfServiceContainer;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.tasks.Builder;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Describes a build step for managing a Delphix Self Service Container
 * These build steps can be added in the job configuration page in Jenkins.
 */
public class SelfServiceBookmarkBuilder extends Builder implements SimpleBuildStep {

    public final String delphixEngine;
    public final String delphixBookmark;
    public final String delphixOperation;
    public final String delphixContainer;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * [SelfServiceBookmarkBuilder description]
     *
     * @param delphixEngine         String
     * @param delphixBookmark       String
     * @param delphixOperation      String
     */
    @DataBoundConstructor
    public SelfServiceBookmarkBuilder(
        String delphixEngine,
        String delphixBookmark,
        String delphixOperation,
        String delphixContainer
    ) {
        this.delphixEngine = delphixEngine;
        this.delphixOperation = delphixOperation;
        this.delphixBookmark = delphixBookmark;
        this.delphixContainer = delphixContainer;
    }

    @Extension
    public static final class RefreshDescriptor extends SelfServiceDescriptor {

        /**
         * Add Engines to drop down
         */
        public ListBoxModel doFillDelphixEngineItems() {
            return super.doFillDelphixEngineItems();
        }

        public ListBoxModel doFillDelphixBookmarkItems(@QueryParameter String delphixEngine) {
            return super.doFillDelphixBookmarkItems(delphixEngine);
        }

        public ListBoxModel doFillDelphixContainerItems(@QueryParameter String delphixEngine) {
            return super.doFillDelphixSelfServiceItems(delphixEngine);
        }

        public ListBoxModel doFillDelphixOperationItems() {
            ListBoxModel operations = new ListBoxModel();
            operations.add("Create","Create");
            operations.add("Update","Update");
            operations.add("Delete","Delete");
            operations.add("Share","Share");
            return operations;
        }

        /**
         * Name to display for build step
         */
        @Override
        public String getDisplayName() {
            return "Delphix - Self Service Bookmark";
        }
    }

    @Override
    public void perform(
        Run<?, ?> run,
        FilePath workspace,
        Launcher launcher,
        TaskListener listener
    ) throws InterruptedException, IOException {
        // Check if the input engine is not valid
        if (delphixBookmark.equals("NULL")) {
            listener.getLogger().println(Messages.getMessage(Messages.INVALID_ENGINE_ENVIRONMENT));
        }

        String engine = delphixEngine;
        String operationType = delphixOperation;
        String bookmark = delphixBookmark;

        if (GlobalConfiguration.getPluginClassDescriptor().getEngine(engine) == null) {
            listener.getLogger().println(Messages.getMessage(Messages.INVALID_ENGINE_ENVIRONMENT));
        }

        DelphixEngine loadedEngine = GlobalConfiguration.getPluginClassDescriptor().getEngine(engine);
        SelfServiceBookmarkRepository bookmarkRepo = new SelfServiceBookmarkRepository(loadedEngine);
        SelfServiceRepository containerRepo = new SelfServiceRepository(loadedEngine);

        JsonNode action = MAPPER.createObjectNode();
        try {
            bookmarkRepo.login();
            switch (operationType) {
                case "Create":
                    containerRepo.login();
                    SelfServiceContainer container = containerRepo.getSelfServiceContainer(delphixContainer);
                    action = bookmarkRepo.create("Created By Jenkins", container.getActiveBranch(), container.getReference());
                    break;
                case "Delete":
                    action = bookmarkRepo.delete(bookmark);
                    break;
                default: throw new DelphixEngineException("Undefined Self Service Bookmark Operation");
            }
        } catch (DelphixEngineException e) {
            // Print error from engine if job fails and abort Jenkins job
            listener.getLogger().println(e.getMessage());
        } catch (IOException e) {
            // Print error if unable to connect to engine and abort Jenkins job
            listener.getLogger().println(
                    Messages.getMessage(Messages.UNABLE_TO_CONNECT, new String[] { bookmarkRepo.getEngineAddress() }));
        }

        //Check for Action with a Completed State
        try {
            ActionStatus actionStatus = bookmarkRepo.getActionStatus(action.get("action").asText());
            if (actionStatus.getState().equals("COMPLETED")){
                String message = actionStatus.getTitle() + ": " + actionStatus.getState();
                listener.getLogger().println(message);
                return;
            }
        } catch (DelphixEngineException e) {
            listener.getLogger().println(e.getMessage());
        } catch (IOException e) {
            listener.getLogger().println(Messages.getMessage(Messages.UNABLE_TO_CONNECT,
                    new String[] { bookmarkRepo.getEngineAddress() }));
        }

        String job = action.get("job").asText();

        // Make job state available to clean up after run completes
        run.addAction(new PublishEnvVarAction(bookmark, engine));
        run.addAction(new PublishEnvVarAction(job, engine));

        JobStatus status = new JobStatus();
        JobStatus lastStatus = new JobStatus();

        // Display status of job
        while (status.getStatus().equals(JobStatus.StatusEnum.RUNNING)) {
            // Get current job status and abort the Jenkins job if getting the
            // status fails
            try {
                status = bookmarkRepo.getJobStatus(job);
            } catch (DelphixEngineException e) {
                listener.getLogger().println(e.getMessage());
            } catch (IOException e) {
                listener.getLogger().println(Messages.getMessage(Messages.UNABLE_TO_CONNECT,
                        new String[] { bookmarkRepo.getEngineAddress() }));
            }

            // Update status if it has changed on Engine
            if (!status.getSummary().equals(lastStatus.getSummary())) {
                listener.getLogger().println(status.getSummary());
                lastStatus = status;
            }
            // Sleep for one second before checking again
            Thread.sleep(1000);
        }
    }
}
