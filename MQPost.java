/*
 * This is an example of how a Java class can be called from Loadrunner to put messages 
 * on an IBM MQ Queue.  All methods are static so they can be called from Loadrunner 
 * without having to instantiate any objects.  There are two messages used in this example.
 * The request data to send is put in text files on the hard drive so they can be changed
 * without having to recompile the Java class.  
 *  
 * This class has very limited functionality, but allows simple use of MQ from Loadrunner 
 * without having to pay for the Loadrunner MQ integration license. Many of the parameters 
 * in this class need to be modified to match your MQ configuration.  This class is meant 
 * as an example of what can be done, and WILL NOT WORK for you by simply compiling it.  
 * 
 * To run this class from Loadrunner, it needs to be compiled into a .jar file.  The .jar
 * file containing this class and the IBM MQ jar files need to be on the class path set up
 * in Loadrunner.  The jar files that were needed to run this code were com.ibm.mq.jar and
 * connector-api-1.5.jar.  Sample MQ messages and a build file are included in the zip file
 * with this sample code.  
 *  
 * The system.out messages are useful in debugging.  You may want to comment them out when
 * it is working ok.  The system.out messages will go into the Loadrunner log file when 
 * executed from Loadrunner. This class has a "main" method so it can be run from the command 
 * line to facilitate troubleshooting.  When you call it from Loadrunner, you should call 
 * the "runit" method directly. 
 * 
 * Version: .80
 * Author: Doug Baber, Kobatera, LLC
 * Date: 10/15/2010
 * License: Public domain - use and modify however you like, no warranty.
 */


package com.kobatera.mq;

import com.ibm.mq.MQC;
import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPoolToken;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import java.io.*;
import java.net.URL;
import java.util.*;

/*
 *  This is a simple class that will post a message on an IBM MQ queue using the 
 *  IBM Java libraries for MQ. This class is intended to be called from Loadrunner to place
 *  one of two messages on the queue.    
 * 
 */
public class MQPost {

	private static MQQueue sendQ_ = null;

	private static MQQueueManager qMgrReq;

	private static String reqQ;
	private static String respQ;

	private static Properties prop_ = null;
	private static String qMgrName_ = null;

	private static final String SMSFILE = "c:\\kobatera\\mq-example\\smsPayload.txt";
	private static final String EMAILFILE = "c:\\kobatera\\mq-example\\emailPayload.txt";
	private static String SMS_MSG = null;
	private static String EMAIL_MSG = null;

	static final int QIO = MQC.MQOO_INPUT_SHARED | MQC.MQOO_FAIL_IF_QUIESCING;

	static final int QOO = MQC.MQOO_OUTPUT | MQC.MQPMO_SET_ALL_CONTEXT
			| MQC.MQOO_FAIL_IF_QUIESCING;

	/*
	 * Set up the connection properties at startup.  Lots of possible ways to handle this,
	 * just chose a static block.
	*/
	static {
		try {
			prop_ = new Properties();
			qMgrName_ = "QTESTQ";
			prop_.put("QUEUE_MANAGER", qMgrName_);

			// Channels for QTESTQ
			String channel = "MQ.CLIENT.02.01";

			prop_.put(MQC.CHANNEL_PROPERTY, channel);
			String port = "1415";

			if (port != null) {
				prop_.put(MQC.PORT_PROPERTY, new Integer(port));
			}
			prop_.put(MQC.HOST_NAME_PROPERTY, "myserver");

			reqQ = "MY.ALRT.ALERT.MESSAGE";
			respQ = "MY.ALRT.ALERT.MESSAGE";
			prop_.put("requestQ", reqQ);
			prop_.put("userId", "testuser");
			prop_.put("putApplicationName", "MYQTEST");

			/*
			 * If MQ is using a secure connection, you would need to set up the
			 * authentication here. This code for SSL was not used, and may not
			 * work correctly.
			 * 
			 * prop_.put(MQC.SSL_CIPHER_SUITE_PROPERTY,"SSL_RSA_WITH_RC4_128_MD5");
			 * String trustStore =
			 * "C:\\bea_sp6\\jrockit-R26_142_11\\jre\\lib\\security\\cacerts";
			 * System.setProperty("javax.net.ssl.trustStore",trustStore);
			 * System.setProperty("javax.net.ssl.trustStorePassword","changeit");
			 * System.setProperty("javax.net.ssl.keyStore","C:\\bea_sp6\\certs\\test-qaid.jks");
			 * System.setProperty("javax.net.ssl.keyStorePassword","weblogic");
			 */
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed static block");
		}
	}

	/*
	 * Static method to initialize the queue.  This is called in the vuser init section
	 * of Loadrunner.  If it isn't called from Loadrunner, the "runit" method will call it 
	 * anyway, so calling it explicitly isn't required.  I just set up the structure to have the
	 * option to do it if future modifications are needed.  
	*/
	public synchronized static void initQueue() throws Exception {
		System.out.println("Entering initQueue");
		
		if (sendQ_ == null) {          // make sure this is only done once
			System.out.println("Setting up queue");
			qMgrReq = new MQQueueManager(qMgrName_, prop_);
			MQQueue sq1 = qMgrReq.accessQueue(reqQ, QOO, null, null, null);

			sendQ_ = sq1;

			System.out.println("Reading SMS data file");
			StringBuffer tmpBuf = new StringBuffer(500);
			String fline;
			BufferedReader pr = new BufferedReader(new FileReader(SMSFILE));
			while ((fline = pr.readLine()) != null) {
				tmpBuf.append(fline);
			}
			SMS_MSG = tmpBuf.toString();

			System.out.println("Reading EMAIL data file");
			tmpBuf = new StringBuffer(500);
			pr = new BufferedReader(new FileReader(EMAILFILE));
			while ((fline = pr.readLine()) != null) {
				tmpBuf.append(fline);
			}
			EMAIL_MSG = tmpBuf.toString();
		}

		System.out.println("Exiting initQueue");
	}

	/**
	 * Post a message on the MQ queue
	 */
	private static String sendMessage(String msg) throws Exception {
		System.out.println("Entering sendMessage");
		String statMsg = "Posted successfully";
		MQPutMessageOptions pmo = new MQPutMessageOptions();
		MQGetMessageOptions pmi = new MQGetMessageOptions();

		pmi.options = MQC.MQGMO_WAIT | MQC.MQGMO_CONVERT;
		pmi.waitInterval = 120000;
		pmi.matchOptions = MQC.MQMO_MATCH_CORREL_ID;
		pmo.options = pmo.options | MQC.MQPMO_NEW_MSG_ID
				| MQC.MQOO_SET_ALL_CONTEXT;
		// Send the request mesage to the request queue
		MQMessage reqMsg = new MQMessage();
		reqMsg.format = MQC.MQFMT_STRING;
		reqMsg.userId = "test";
		reqMsg.putApplicationName = "MYTEST";
		reqMsg.expiry = 60000000;
		reqMsg.write(msg.getBytes());
		reqMsg.replyToQueueName = respQ;
		sendQ_.put(reqMsg, pmo);

		System.out.println("Exiting sendMessage");
		return statMsg;
	}

	/*
	 * This is the method to be called from Loadrunner with MQPost.runit("msgType").  Since this 
	 * sample code uses two different messages, this method is called passing in the type of message 
	 * that you want to send.  If it is the "SMS" message, it will put the text from the SMS text 
	 * file on the appropriate queue.  If it is called with anything else, it will put the text from 
	 * the Email text file on the appropriate queue.  
	 *   
	*/
	public static void runit(String msgType) throws Exception {
		System.out.println("Entering runit: Message type: " + msgType);

		if (sendQ_ == null) {
			initQueue();
		}

		String mqMsg = null;

		if (msgType == "SMS") {
			mqMsg = SMS_MSG;
		} else {
			mqMsg = EMAIL_MSG;
		}

		try {
			System.out.println("Posting message: " + mqMsg);
			String resp = MQPost.sendMessage(mqMsg);
			System.out.println("Response from sendMessage is: " + resp);
		} catch (Exception e) {
			System.out.println("In catch block");
			e.printStackTrace();
		}
		
		System.out.println("Exiting runit");
	}
	

	public static void main( String[] args ) throws Exception
	{
		if (args.length >= 1){
			MQPost.runit(args[0]);
		} else {
			MQPost.runit("");
		}
	}
}
