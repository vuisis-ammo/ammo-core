
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
