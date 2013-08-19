package edu.vu.isis.ammo.core.distributor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.WireFormat;
import com.google.protobuf.WireFormatUtils;

import edu.vu.isis.ammo.core.pb.AmmoMessages.DataMessage;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper;
import edu.vu.isis.ammo.core.pb.AmmoMessages.MessageWrapper.MessageType;
import edu.vu.isis.ammo.core.pb.AmmoMessages.PullResponse;
import edu.vu.isis.ammo.util.ByteBufferAdapter;
import edu.vu.isis.ammo.util.ByteBufferInputStream;
import edu.vu.isis.ammo.util.ByteBufferOutputStream;
import edu.vu.isis.ammo.util.NioByteBufferAdapter;

/**
 * Works around some protobuf inefficiencies in some really gross ways
 * 
 * @author mriley
 */
public final class AmmoMessageSerializer {
	
	
	private static final int DATA_MESSAGE_DATA_FIELD_TAG = WireFormatUtils.makeTag(DataMessage.DATA_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
	private static final int PULL_RESPONSE_DATA_FIELD_TAG = WireFormatUtils.makeTag(PullResponse.DATA_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
	private static final int DATA_MESSAGE_FIELD_TAG = WireFormatUtils.makeTag(MessageWrapper.DATA_MESSAGE_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
	private static final int PULL_RESPONSE_MESSAGE_FIELD_TAG = WireFormatUtils.makeTag(MessageWrapper.PULL_RESPONSE_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
	private static final int TYPE_MESSAGE_FIELD_TAG = WireFormatUtils.makeTag(MessageWrapper.TYPE_FIELD_NUMBER, WireFormat.WIRETYPE_VARINT);
	private static final int EOF = 0;
	
	private static CodedInputStream stream;
	private static Field bufferSizeField;
	private static Field bufferPosField;
	private static Field totalBytesRetiredField;
	private static Field inputField;
	
	
	/**
	 * Return the correct payload as a {@link ByteBufferAdapter}
	 * 
	 * @param payload
	 * @param data
	 * @return
	 */
	public static ByteBufferAdapter getDataBuffer( ByteBufferAdapter payload, ByteString data ) {
	    if( data == null || data == ByteString.EMPTY ) {
	    	return payload;
	    } else {
	    	return new NioByteBufferAdapter(ByteBuffer.wrap(data.toByteArray()));
	    }
	}
	
	/**
	 * This too is crap
	 * 
	 * Use this to build a bytebuffer that contains the payload.  DO NOT add the data as a
	 * {@link ByteString} to the message
	 * 
	 * @param payload
	 * @return
	 * @throws IOException
	 */
	public static ByteBufferAdapter serialize( final MessageWrapper.Builder mw, 
			ByteBufferAdapter payload ) throws IOException {
		DataMessage dataMessage = mw.getDataMessage();
		mw.clearDataMessage();
		MessageWrapper messageWrapper = mw.buildPartial();	
		
		int fullSize = payload.remaining(); // data chunk
		fullSize += messageWrapper.getSerializedSize(); // mw stuff
		fullSize += dataMessage.getSerializedSize(); // data message
		fullSize += 4; // at most 4 bytes for payload size
		fullSize += 4; // at most 4 bytes for payload tag
		fullSize += 4; // at most 4 bytes for data message size
		fullSize += 4; // at most 4 bytes for data message tag
		
		// write message wrapper
		ByteBufferAdapter fullMessage = ByteBufferAdapter.obtain(fullSize);
		CodedOutputStream writer = CodedOutputStream.newInstance(new ByteBufferOutputStream(fullMessage));
		messageWrapper.writeTo(writer);
		
		// write data message
		writer.writeTag(MessageWrapper.DATA_MESSAGE_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
		writer.writeRawVarint32(dataMessage.getSerializedSize() + payload.remaining() 
				+ CodedOutputStream.computeRawVarint32Size(payload.remaining())
				+ CodedOutputStream.computeTagSize(DataMessage.DATA_FIELD_NUMBER));
	    dataMessage.writeTo(writer);
		
	    // write data message data
	    writer.writeTag(DataMessage.DATA_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
		writer.writeInt32NoTag(payload.remaining());
		writer.flush();
		fullMessage.put(payload);
		
		// done?
		payload.release();
		fullMessage.flip();
		return fullMessage;
	}
	
	/**
	 * This is crap.
	 * 
	 * We don't want a message that contains a large "data" portion to be read onto the heap
	 * into a ByteString.  Sometimes the device will run out of memory when there are many
	 * queued messages containing large {@link ByteString}s of data.  This way we reduce the
	 * amount of heap memory used per message and cut down on the amount of GC due to copied
	 * byte[]s. 
	 * 
	 * TODO: With a little work we might be able to generalize this.  We can create a class that
	 * identifies the type and tag info for a message that contains a large data buffer.  This
	 * could then be a little more generic in the way that it handles parsing the buffer to build
	 * the message.
	 * 
	 * @param payload
	 * @return
	 */
	public static MessageWrapper deserialize( ByteBufferAdapter payload ) throws IOException {
		// mark here so we can rewind
		int initialPosition = payload.position();
		
		// first, check the type of message.  If this is a pull response or data message
		// (ie, a message with a potentially large payload) make sure to deserialize it
		// so that we skip the large payload and adjust the buffer so that the position
		// is at the start of the payload and the limit is at the end of the payload
		CodedInputStream codedStream = createStream(new ByteBufferInputStream(payload));
		for( ;; ) {
			int tag = codedStream.readTag();
			if( tag == EOF ) {
				break;
				
			} else if( tag == TYPE_MESSAGE_FIELD_TAG ) {
				int readEnum = codedStream.readEnum();    			
				MessageType value = MessageType.valueOf(readEnum);
				if( value != MessageType.PULL_RESPONSE && value != MessageType.DATA_MESSAGE ) {
					break;
				}	
				
			} else if( tag == PULL_RESPONSE_MESSAGE_FIELD_TAG ) {
				payload.position(getPosition(codedStream, initialPosition));
				
				PullResponse pullMessage = deserializeMessage(payload, 
						PULL_RESPONSE_DATA_FIELD_TAG, PullResponse.newBuilder());
				
				return MessageWrapper.newBuilder()
						.setType(MessageType.PULL_RESPONSE)
						.setPullResponse(pullMessage).buildPartial();
				
			} else if( tag == DATA_MESSAGE_FIELD_TAG ) {
				payload.position(getPosition(codedStream, initialPosition));
				
				DataMessage dataMessage = deserializeMessage(payload, 
						DATA_MESSAGE_DATA_FIELD_TAG, DataMessage.newBuilder());
				
				return MessageWrapper.newBuilder()
						.setType(MessageType.DATA_MESSAGE)
						.setDataMessage(dataMessage).buildPartial();
			} else {
				codedStream.skipField(tag);
			}
		}
	
		
		// otherwise, we deserialize the normal way
		payload.position(initialPosition);
		return MessageWrapper.parseFrom(createStream(new ByteBufferInputStream(payload)));
	}
	
	/**
	 * Deserializes a message with a "data" tag, skipping the data tag and positioning
	 * the buffer so that it's position() is at the begining of the data and it's limit()
	 * is at the end of the data.
	 * 
	 * 
	 * @param payload
	 * @param messageStart
	 * @param dataFieldTag
	 * @param builder
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private static <T extends GeneratedMessage> T deserializeMessage( 
			ByteBufferAdapter payload, int dataFieldTag,
			GeneratedMessage.Builder<?> builder ) throws IOException {
		
		// position the stream at the start of the message
		int start = payload.position();
		CodedInputStream codedStream = createStream(new ByteBufferInputStream(payload));
		int length = codedStream.readRawVarint32();
		int messageStart = getPosition(codedStream, start);
		int messageEnd = messageStart + length;
		int dataMessageDataStart = -1;
		int dataMessageDataEnd = -1;
		
		// search through the payload until we find the tag that
		// marks the start of the data.  The length of the data will
		// be written to the stream as a varint32.  We use the length
		// to determine the end of the data field.  Note that this
		// skips some internal protobuf checks but should be a bit more
		// efficient.
		for( ;; ) {
			int position = getPosition(codedStream, start);
			int tag = codedStream.readTag();
			if( tag == EOF ) {
				break;
			} else if( tag == dataFieldTag ) {
				dataMessageDataStart = position;
				int len = codedStream.readRawVarint32();
				dataMessageDataEnd = getPosition(codedStream, start) + len;
				break;
			} else {
				codedStream.skipField(tag);
			}
		}
		
		// make a head slice
		payload.position(messageStart);
		ByteBufferAdapter head = payload.slice();
		head.limit(dataMessageDataStart-messageStart);        		
		
		// make a tail slice
		payload.position(dataMessageDataEnd);
		ByteBufferAdapter tail = payload.slice();
		tail.limit(messageEnd-dataMessageDataEnd);
		
		// now make a message from those parts
		builder.mergeFrom(new ByteBufferInputStream(head));
		builder.mergeFrom(new ByteBufferInputStream(tail));
		T ret = (T) builder.buildPartial();
		
		// now position the payload so that it wraps the data portion
		payload.position(dataMessageDataStart);
		codedStream = createStream(new ByteBufferInputStream(payload));
		codedStream.readTag(); // read the tag to advance past that
		codedStream.readRawVarint32(); // read the size to advance past that
		payload.position(getPosition(codedStream, dataMessageDataStart));
		payload.limit(dataMessageDataEnd);
		
		return ret;
	}
	
	/**
	 * Get the actual position that the byte buffer would be on if the coded stream were not buffered.  
	 * 
	 * @param stream
	 * @param start
	 * @return
	 */
	private static int getPosition( CodedInputStream stream, int start ) {
		return start + stream.getTotalBytesRead();
	}
	
	/**
	 * Creates or reuses a {@link CodedInputStream} for reading messages.  By reusing the stream we
	 * cut down on loads of unnecessary GC.  
	 * 
	 * @param in
	 * @return
	 */
	private static CodedInputStream createStream( InputStream in ) {
		try {
			if( stream == null ) {
				stream = CodedInputStream.newInstance(in);
				bufferSizeField = stream.getClass().getDeclaredField("bufferSize");
				bufferPosField = stream.getClass().getDeclaredField("bufferPos");
				totalBytesRetiredField = stream.getClass().getDeclaredField("totalBytesRetired");
				inputField = stream.getClass().getDeclaredField("input");
				bufferSizeField.setAccessible(true);
				bufferPosField.setAccessible(true);
				totalBytesRetiredField.setAccessible(true);
				inputField.setAccessible(true);
			} else {
				bufferSizeField.set(stream, 0);
				bufferPosField.set(stream, 0);
				totalBytesRetiredField.set(stream, 0);
				inputField.set(stream, in);
			}
		} catch ( Exception e ) {
			return CodedInputStream.newInstance(in);
		}
		return stream;
	}
}

