package edu.vu.isis.ammo.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Internet Media Type utilities. (a.k.a. MIME type or Content Type)
 */

public class InternetMediaType {

    private String type;
    private String subtype;
    private Map<String,String> params;

    private InternetMediaType(String type, String subtype, String[] params) {
        this.type = type;
        this.subtype = subtype;
        if (params == null) {
            this.params = null;
            return;
        }
        this.params = new HashMap<String,String>();
    }

    @Override
    public String toString() {
        if (params == null) return type + "/" + subtype;
        StringBuilder sb = new StringBuilder();
        for (String key : params.keySet()) {
            sb.append(key).append("=").append(params.get(key)).append("; ");
        }
        return sb.toString();
    }

    public static InternetMediaType getInst(String fullType) {
        int index = fullType.indexOf('/');
        if (index < 0)
            return  new InternetMediaType(fullType.toLowerCase(), null, null);

        return new InternetMediaType(
                   fullType.substring(0,index).toLowerCase(),
                   fullType.substring(index+1).toLowerCase(),
                   null);
//	  StringTokenizer tokenizer = new StringTokenizer(mediaType, ";");
//
//	    tokenizer.nextToken();
//
//	    while (tokenizer.hasMoreTokens())
//	    {
//	      String	parameter = tokenizer.nextToken().trim();
//	      int	index = parameter.indexOf('=');
//
//	      if (index < 0) continue;
//        if (! name.equalsIgnoreCase(parameter.substring(0, index).trim())) continue;
//
//	        StringTokenizer	value =
//	          new StringTokenizer(parameter.substring(index + 1), " \"'");
//
//	        if (value.countTokens() == 1) return value.nextToken();
//	    }
    }

    public String getType() {
        return type;
    }
    public InternetMediaType setType(String type) {
        this.type = type;
        return this;
    }

    public String getSubtype() {
        return subtype;
    }
    public InternetMediaType setSubtype(String subtype) {
        this.subtype = subtype;
        return this;
    }


}