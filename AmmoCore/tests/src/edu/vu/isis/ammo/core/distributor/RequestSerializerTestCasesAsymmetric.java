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



package edu.vu.isis.ammo.core.distributor;

import java.util.ArrayList;

import android.content.ContentValues;

public class RequestSerializerTestCasesAsymmetric {

    // 3 test sets
    static ArrayList<ContentValues> inputCVs;
    static ArrayList<ContentValues> jsonResultCVs;
    static ArrayList<ContentValues> terseResultCVs;
    static ArrayList<String> mimeTypes;
    static ArrayList<String> description;

    // schema info
    public class schema {
        public static final String topic = "topic";
        public static final String sender = "sender";
        public static final String recipient = "recipient";
        public static final String thread = "thread";
        public static final String payload = "payload";
        public static final String created_date = "created date";
        public static final String msg_type = "msg type";
    }

    public static ArrayList<ContentValues> getInputCVsForRoundTripTests() {
        return inputCVs;
    }

    public static ArrayList<ContentValues> getResultCVsForTerseRoundTripTests() {
        return terseResultCVs;
    }
    
    public static ArrayList<ContentValues> getResultCVsForJsonRoundTripTests() {
        return jsonResultCVs;
    }

    public static ArrayList<String> getMimeTypesForRoundTripTests() {
        return mimeTypes;
    }

    public static ArrayList<String> getDescriptionsForRoundTripTests() {
        return description;
    }

    // putting the tests into the 3 test sets
    static {
        inputCVs = new ArrayList<ContentValues>();
        jsonResultCVs = new ArrayList<ContentValues>();
        terseResultCVs = new ArrayList<ContentValues>();
        mimeTypes = new ArrayList<String>();
        description = new ArrayList<String>();

        /*
         * test 3 mimeTypes with default values
         */
        {
            ContentValues input = createEmptyCV();
            // add input
            input.put(schema.topic,"test text");
            input.put(schema.sender,"test text");
            input.put(schema.recipient,"test text");
            input.put(schema.thread,"123456");
            input.put(schema.payload,"test text");
            input.put(schema.msg_type,"test text");
            
            inputCVs.add(input);
            ContentValues result = createEmptyCV();
            // add result for json
            jsonResultCVs.add(result);
            // add result for terse
            terseResultCVs.add(result);
            // mime type =
            mimeTypes.add("edu.vu.isis.ammo.encoding_test_001.test_1");
            description.add("edu.vu.isis.ammo.encoding_test_001.test_1 test");
        }
    }

    // "edu.vu.isis.quick"
    public static ContentValues createEmptyCV() {
        final ContentValues cv = new ContentValues();
        return cv;
    }
}
