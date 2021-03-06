/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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

  public String getType() { return type;  }
  public InternetMediaType setType(String type) { this.type = type; return this; }
  
  public String getSubtype() { return subtype;  }
  public InternetMediaType setSubtype(String subtype) { this.subtype = subtype; return this; }


} 