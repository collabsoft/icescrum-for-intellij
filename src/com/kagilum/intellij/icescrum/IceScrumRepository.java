/*
* Copyright 20010-20012 Kagilum
*
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
*
* Author : Vincent Barrier (vbarrier@kagilum.com)
*
*/

package com.kagilum.intellij.icescrum;

import com.google.gson.*;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class IceScrumRepository extends BaseRepositoryImpl {

    private String pkey;
    private String serverUrl = null;

    public IceScrumRepository() {
        super();
    }

    public IceScrumRepository(IceScrumRepositoryType type) {
        super(type);
        Protocol easyhttps = new Protocol("https", new EasySSLProtocolSocketFactory(), 443);
        Protocol.registerProtocol("https", easyhttps);
        this.setUseHttpAuthentication(true);
    }

    private IceScrumRepository(IceScrumRepository other) {
        super(other);
        extractSettings(other.getUrl());
    }

    @Override
    public Task[] getIssues(@Nullable String s, int i, long l) throws Exception {
        String url = createCompleteUrl();
        GetMethod method = new GetMethod(url);
        configureHttpMethod(method);
        method.setRequestHeader("Content-type","text/json");

        getHttpClient().executeMethod(method);
        int code = method.getStatusCode();
        if (code != HttpStatus.SC_OK) {
            checkServerStatus(code);
        }

        JsonElement json = new JsonParser().parse(method.getResponseBodyAsString());
        JsonArray array= json.getAsJsonArray();
        Iterator iterator = array.iterator();
        List<Task> tasks = new ArrayList<Task>();
        while(iterator.hasNext()){
            JsonObject element = (JsonObject)iterator.next();
            IceScrumTask task = new IceScrumTask(element, this.serverUrl, this.pkey);
            tasks.add(task);
        }
        return tasks.toArray(new Task[]{});
    }

    @Override
    public Task findTask(String s) throws Exception {
        return null;
    }

    @Override
    public BaseRepository clone() {
        return new IceScrumRepository(this);
    }

    @Override
    public CancellableConnection createCancellableConnection() {
        extractSettings(getUrl());
        GetMethod method = new GetMethod(createCompleteUrl());
        return new HttpTestConnection<GetMethod>(method) {
            @Override
            public void doTest(GetMethod method) throws Exception {
                if (!isConfigured()){
                    throw new IOException("Setting missing...");
                }
                method.setRequestHeader("Content-type","text/json");
                HttpClient client = getHttpClient();
                client.executeMethod(method);
                int statusCode = method.getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    checkServerStatus(statusCode);
                }
                Task[] results = getIssues(null, 1, 0);
                if (results.length < 1)
                    throw new IOException("No results fetched, sure this is correct settings?");
            }
        };
    }

    private String createCompleteUrl() {
        return this.serverUrl + "/ws/p/" + this.pkey + "/task/";
    }

    private void checkServerStatus(int code) throws IOException {
        switch(code){
            case HttpStatus.SC_SERVICE_UNAVAILABLE:
                throw new IOException("Web services aren't activated on your project...");
            case HttpStatus.SC_UNAUTHORIZED:
                throw new IOException("Wrong login/pass...");
            case HttpStatus.SC_FORBIDDEN:
                throw new IOException("You haven't access to this project...");
            case HttpStatus.SC_NOT_FOUND:
                throw new IOException("No project or iceScrum server found...");
            default:
                throw new IOException("Server error (" + HttpStatus.getStatusText(code) + ")");
        }
    }

    private boolean extractSettings(String url) {
        if (url.indexOf("/p/") > 0){
            this.serverUrl = url.substring(0, url.indexOf("/p/"));
        }else {
            this.serverUrl = null;
            this.pkey = null;
            return false;
        }
        Pattern p = Pattern.compile("\\d*[A-Z][A-Z0-9]*");
        Matcher m = p.matcher(url);
        if (m.find()){
            this.pkey = m.group(0);
            return true;
        }else{
            this.pkey = null;
            return false;
        }
    }

    @Override
    public boolean isConfigured() {
        boolean hasCredentials = (isNotEmpty(myUsername) && isNotEmpty(myPassword));
        return hasCredentials && extractSettings(getUrl());
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return "iceScrum: " + (this.pkey == null ? "invalid parameters" : this.pkey);
    }


}
