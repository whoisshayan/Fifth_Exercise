package org.example;


import java.util.Map;


/**
 * JSON line‑delimited messages. Minimal shape:
 * Request: {"type":"cmd", "cmd":"login", "args":{...}, "token":"...optional..."}
 * Response: {"ok":true, "data":{...}} or {"ok":false, "error":"..."}
 * Push: {"push":true, "event":"task_added", "data":{...}}
 */
public final class Protocol {
    public static class Request {
        public String type = "cmd"; // reserved
        public String cmd;
        public Map<String,Object> args;
        public String token; // optional
    }
    public static class Response {
        public boolean ok;
        public Object data; // or String error
        public String error;


        public static Response ok(Object data){
            Response r = new Response(); r.ok = true; r.data = data; return r;
        }
        public static Response err(String msg){
            Response r = new Response(); r.ok = false; r.error = msg; return r;
        }
    }
    public static class Push {
        public boolean push = true;
        public String event; // e.g., task_added, task_updated
        public Object data;
    }
}