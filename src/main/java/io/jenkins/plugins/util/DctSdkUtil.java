package io.jenkins.plugins.util;

import java.io.PrintStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import com.delphix.dct.ApiClient;
import com.delphix.dct.ApiException;
import com.delphix.dct.api.JobsApi;
import com.delphix.dct.api.VdbsApi;
import com.delphix.dct.models.DeleteVDBParameters;
import com.delphix.dct.models.DeleteVDBResponse;
import com.delphix.dct.models.Job;
import com.delphix.dct.models.ProvisionVDBBySnapshotParameters;
import com.delphix.dct.models.ProvisionVDBFromBookmarkParameters;
import com.delphix.dct.models.ProvisionVDBResponse;

import hudson.model.Run;
import io.jenkins.plugins.delphix.DelphixGlobalConfiguration;

import jenkins.model.Jenkins;
import static io.jenkins.plugins.util.CredentialUtil.getApiKey;

public class DctSdkUtil {

    public ApiClient createApiClient(Run<?, ?> run, String credId, PrintStream logger) {
        String url = getGlobalConfig().getDctUrl();
        if (url == null) {
            logger.println("Delphix Global Configuration Missing");
            return null;
        }
        String apiKey = getApiKey(credId, run);
        if (apiKey == null) {
            logger.println("Cannot find any credentials for " + credId);
            return null;
        }
        ApiClient defaultClient = null;
        try {
            defaultClient = ApiClientInit.init();
            defaultClient.setApiKey(apiKey);
            defaultClient.setBasePath(url);
        } catch (KeyManagementException | NoSuchAlgorithmException | ApiException e) {
            logger.println("ApiClient Creation Exception: " + e.getMessage());
            e.printStackTrace();
        }
        return defaultClient;
    }

    public ProvisionVDBResponse provisionVdbFromBookmark(ApiClient defaultClient,
            ProvisionVDBFromBookmarkParameters provisionVDBFromBookmarkParameters)
            throws ApiException {
        ProvisionVDBResponse result = null;
        VdbsApi apiInstance = new VdbsApi(defaultClient);
        result = apiInstance.provisionVdbFromBookmark(provisionVDBFromBookmarkParameters);
        return result;
    }

    public ProvisionVDBResponse provisionVdbBySnapshot(ApiClient defaultClient,
            ProvisionVDBBySnapshotParameters provisionVDBBySnapshotParameters) throws ApiException {
        ProvisionVDBResponse result = null;
        VdbsApi apiInstance = new VdbsApi(defaultClient);
        result = apiInstance.provisionVdbBySnapshot(provisionVDBBySnapshotParameters);
        return result;
    }

    public DeleteVDBResponse deleteVdb(ApiClient defaultClient, String vdbId, Boolean force)
            throws ApiException {
        VdbsApi apiInstance = new VdbsApi(defaultClient);
        DeleteVDBParameters deleteVDBParameters = new DeleteVDBParameters();
        deleteVDBParameters.setForce(force);
        DeleteVDBResponse result = apiInstance.deleteVdb(vdbId, deleteVDBParameters);
        System.out.println(result);
        return result;

    }

    private DelphixGlobalConfiguration.DescriptorImpl getGlobalConfig() {
        DelphixGlobalConfiguration.DescriptorImpl delphixGlobalConfig = Jenkins.get()
                .getDescriptorByType(DelphixGlobalConfiguration.DescriptorImpl.class);
        return delphixGlobalConfig;
    }

    public String waitForPolling(ApiClient defaultClient, String jobId, PrintStream logger)
            throws ApiException {
        final long WAIT_TIME = 20000;
        boolean completed = false;
        String status = null;
        JobsApi apiInstance = new JobsApi(defaultClient);
        while (!completed) {
            Job result = apiInstance.getJobById(jobId);
            // result != null && result.getStatus() != null &&
            logger.println("Current Job Status: " + result.getStatus());
            if (!result.getStatus().toString().equals("STARTED")) {
                completed = true;
                status = result.getStatus().toString();
            }
            if (completed) {
                break;
            }
            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException ex) {
                logger.println("Wait interrupted!");
                logger.println(ex.getMessage());
                completed = true; // bail out of wait loop
            }
        }
        return status;
    }

}
