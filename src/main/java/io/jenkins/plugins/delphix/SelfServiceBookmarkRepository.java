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
import io.jenkins.plugins.delphix.objects.SelfServiceBookmark;

import java.io.IOException;
import java.util.LinkedHashMap;
import org.apache.http.client.ClientProtocolException;
import org.kohsuke.stapler.DataBoundConstructor;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Used for interacting with Self Service Bookmarks
 */
public class SelfServiceBookmarkRepository extends DelphixEngine {

    private static final String PATH_ROOT = "/resources/json/delphix/jetstream/bookmark";

    @DataBoundConstructor
    public SelfServiceBookmarkRepository(String engineAddress, String engineUsername, String enginePassword) {
        super(engineAddress, engineUsername, enginePassword);
    }

    public SelfServiceBookmarkRepository(DelphixEngine engine) {
        super(engine);
    }

    /**
     * List Bookmarks in the Delphix Engine
     *
     * @return LinkedHashMap
     *
     * @throws ClientProtocolException [description]
     * @throws IOException             [description]
     * @throws DelphixEngineException  [description]
     */
    public LinkedHashMap<String, SelfServiceBookmark> listBookmarks()
        throws ClientProtocolException, IOException, DelphixEngineException {
            LinkedHashMap<String, SelfServiceBookmark> bookmarks = new LinkedHashMap<String, SelfServiceBookmark>();
            JsonNode bookmarksJSON = this.list();

            for (int i = 0; i < bookmarksJSON.size(); i++) {
                JsonNode bookmarkJSON = bookmarksJSON.get(i);
                SelfServiceBookmark bookmark = SelfServiceBookmark.fromJson(bookmarkJSON);
                bookmarks.put(bookmark.getReference(), bookmark);
            }

            return bookmarks;
    }

    /**
     *  List Bookmarks
     *
     * @return                        JsonNode
     * @throws IOException            [description]
     * @throws DelphixEngineException [description]
     */
    public JsonNode list() throws IOException, DelphixEngineException {
        JsonNode result = engineGET(PATH_ROOT).get(FIELD_RESULT);
        return result;
    }

    /**
     * Delete a Self Service Bookmark by Reference
     *
     * @param  bookmarkRef            String
     * @return                        JsonNode
     * @throws IOException            [description]
     * @throws DelphixEngineException [description]
     */
    public JsonNode delete(String bookmarkRef) throws IOException, DelphixEngineException {
        JsonNode result = enginePOST(
            String.format(PATH_ROOT + "/%s/delete", bookmarkRef),
            "{}"
        );
        return result;
    }

    /**
     * Create a new Self Service Bookmark
     *
     * @param  name                   String
     * @param  branch                 String
     * @param  sourceDataLayout       String
     * @return                        JsonNode
     * @throws IOException            [description]
     * @throws DelphixEngineException [description]
     */
    public JsonNode create(String name, String branch, String sourceDataLayout) throws IOException, DelphixEngineException {
        String request = "{"
        + "\"type\": \"JSBookmarkCreateParameters\","
        + "\"bookmark\": { \"type\": \"JSBookmark\","
        + "\"name\": \"" + name + "\","
        + "\"branch\": \"" + branch + "\""
        + "},\"timelinePointParameters\": {\"type\": \"JSTimelinePointLatestTimeInput\","
        + "\"sourceDataLayout\": \"" + sourceDataLayout + "\""
        + "}}";
        JsonNode result = enginePOST(PATH_ROOT, request);
        return result;
    }

/*

=== POST /resources/json/delphix/jetstream/bookmark ===
{
    "type": "JSBookmarkCreateParameters",
    "bookmark": {
        "type": "JSBookmark",
        "name": "TestFromCLI",
        "branch": "JS_BRANCH-4"
    },
    "timelinePointParameters": {
        "type": "JSTimelinePointLatestTimeInput",
        "sourceDataLayout": "CTO-Source"
    }
}
*/


}
