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

import com.google.gson.JsonObject;
import com.intellij.tasks.Comment;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.TaskType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IceScrumTask extends com.intellij.tasks.Task {

    private static final int STATE_DONE = 2;
    private static final int TYPE_RECURRENT = 10;
    private static final int STATE_WAIT = 0;
    private static final int STATE_BUSY = 1;
    private static final int TYPE_URGENT = 11;

    private final JsonObject taskData;
    private final String serverUrl;
    private final String pkey;

      public IceScrumTask(@NotNull JsonObject taskData, String serverUrl, String pkey) {
        this.taskData = taskData;
        this.serverUrl = serverUrl;
        this.pkey = pkey;
      }



    @NotNull
    @Override
    public String getId() {
        return "T"+taskData.get("uid").getAsString();
    }

    @NotNull
    @Override
    public String getSummary() {
        return taskData.get("name").getAsString();
    }

    @Override
    public String getDescription() {
        String taskDescription = taskData.get("description").isJsonNull() ? "" : taskData.get("description").getAsString();
        taskDescription = taskDescription + "<br/>" + getIceScrumInfos();
        return taskDescription;
    }

    @NotNull
    @Override
    public Comment[] getComments() {
        return Comment.EMPTY_ARRAY;
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @NotNull
    @Override
    public TaskType getType() {
        int type = taskData.get("type").isJsonNull() ? -1 : taskData.get("type").getAsInt();
        return type == TYPE_URGENT ? TaskType.BUG : (type == TYPE_RECURRENT ? TaskType.OTHER : TaskType.FEATURE);
    }

    @Override
    public Date getUpdated() {
        return parseDateISO8601(taskData.get("lastUpdated").getAsString());
    }

    @Override
    public Date getCreated() {
        return parseDateISO8601(taskData.get("creationDate").getAsString());
    }

    @Override
    public boolean isClosed() {
        return taskData.get("state").getAsInt() >= STATE_DONE;
    }

    @Override
    public boolean isIssue() {
        return true;
    }

    @Override
    public String getIssueUrl() {
        return this.serverUrl+"/p/"+pkey+"-"+this.getId();
    }

    @Override
    public TaskState getState(){
        int state = this.taskData.get("state").getAsInt();
        return state == STATE_WAIT ? TaskState.OPEN : state == STATE_BUSY ? TaskState.IN_PROGRESS : TaskState.RESOLVED;
    }

    private String getIceScrumInfos(){
        String desc = "";
        if (getType() == TaskType.FEATURE){
            String id = ((JsonObject)taskData.get("parentStory")).get("id").getAsString();
            desc += "<br/><b>Story: </b>"+"<a href='"+this.serverUrl+"/p/"+this.pkey+"#story/"+id+"' target='_blank'>View associated story</a><br/>";
        }
        desc += "<br/><b>State:</b> ";
        if (getState() == TaskState.OPEN){
            desc += "Todo<br/>";
        }else if(getState() == TaskState.IN_PROGRESS){
            desc += "In progress<br/>";
        }else{
            desc += "Done<br/>";
        }
        if (!taskData.get("estimation").isJsonNull()){
            desc += "<br/><b>Remaining time: </b>"+taskData.get("estimation").getAsString()+" (<a href='"+getIssueUrl()+"'>Update</a>)";
        }
        String sprintId = ((JsonObject)this.taskData.get("backlog")).get("id").getAsString();
        desc += "<br/>"+"<a href='"+this.serverUrl+"/p/"+this.pkey+"#sprintPlan/"+sprintId+"'>View in sprint plan</a>";
        return desc;
    }

    public static Date parseDateISO8601(String input) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
        if (input.endsWith("Z")) {
            input = input.substring(0, input.length() - 1) + "GMT-00:00";
        } else {
            int inset = 6;

            String s0 = input.substring(0, input.length() - inset);
            String s1 = input.substring(input.length() - inset, input.length());

            input = s0 + "GMT" + s1;
        }
        try {
            return df.parse(input);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
