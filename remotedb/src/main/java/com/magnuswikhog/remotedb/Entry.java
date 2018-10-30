package com.magnuswikhog.remotedb;

import org.json.JSONException;
import org.json.JSONObject;

public class Entry extends JSONObject {

    public Entry(){
        super();
    }

    public Entry(String json) throws JSONException {
        super(json);
    }

    @Override
    public Entry put(String name, int value){
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }

    @Override
    public Entry put(String name, boolean value) {
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }

    @Override
    public Entry put(String name, double value) {
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }

    @Override
    public Entry put(String name, long value) {
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }

    @Override
    public Entry put(String name, Object value) {
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }

    @Override
    public Entry putOpt(String name, Object value) {
        try { super.put(name, value); } catch(JSONException e){ e.printStackTrace(); }
        return this;
    }
}
