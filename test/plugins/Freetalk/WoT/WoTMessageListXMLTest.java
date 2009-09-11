package plugins.Freetalk.WoT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.HashSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import plugins.Freetalk.Board;
import plugins.Freetalk.DatabaseBasedTest;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.WoT.WoTMessageListXML;
import plugins.Freetalk.WoT.WoTMessageManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.WoT.WoTOwnMessage;
import plugins.Freetalk.WoT.WoTOwnMessageList;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.keys.FreenetURI;
import freenet.support.MultiValueTable;

public class WoTMessageListXMLTest extends DatabaseBasedTest {
	
	private WoTMessageManager mMessageManager;
	
	private String mMessageListID;
	
		
	/**
	 * Stores the list of boards for each test message.
	 * Key = Message ID
	 * Value = Board name
	 */
	private MultiValueTable<String, String> mMessageBoards = new MultiValueTable<String, String>(16);
	
	
	private String mHardcodedEncodedMessageList;

	@SuppressWarnings("deprecation")
	public void setUp() throws Exception {
		super.setUp();
		
		HashSet<Board> myBoards1 = new HashSet<Board>(); myBoards1.add(new Board("en.board1")); myBoards1.add(new Board("en.board2"));
		HashSet<Board> myBoards2 = new HashSet<Board>(); myBoards2.add(new Board("en.board3")); myBoards2.add(new Board("en.board4"));
		HashSet<Board> myBoards3 = new HashSet<Board>(); myBoards3.add(new Board("en.board5")); myBoards3.add(new Board("en.board6"));
		
		FreenetURI authorRequestSSK = new FreenetURI("SSK@nU16TNCS7~isPTa9gw6nF8c3lQpJGFHA2KwTToMJuNk,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQACAAE/");
		FreenetURI authorInsertSSK = new FreenetURI("SSK@Ykhv0x0K8jtrgOlqWVS4S2Jvmnm64zv5voNjMfz1nYI,FjCiOUGSl6ipOE9glNai9WCp1vPM8k181Gjw62HhYSo,AQECAAE/");
		String authorID = WoTIdentity.getIDFromURI(authorRequestSSK);
		WoTOwnIdentity myAuthor = new WoTOwnIdentity(authorID, authorRequestSSK, authorInsertSSK, "Nickname");
		
		mMessageManager = new WoTMessageManager(db, null);
		
		WoTOwnMessage[] messages = new WoTOwnMessage[] {
			mMessageManager.postMessage(null, null, myBoards1, null, myAuthor, "title1", new Date(2009-1900, 06-1, 01), "text1", null),
			mMessageManager.postMessage(null, null, myBoards2, null, myAuthor, "title2", new Date(2008-1900, 05-1, 02), "text2", null),
			mMessageManager.postMessage(null, null, myBoards3, null, myAuthor, "title3", new Date(2007-1900, 04-1, 03),"text3", null),
		};
	
		FreenetURI[] messageURIs = new FreenetURI[] {
			new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8"),
			new FreenetURI("CHK@0YUT4BEorqJCETQrLSgHBcw5RL7KQNm6Fbpo3ThzTy4,6RzUH23~TwPQ0IDQcgPoxEYX7yBTgTNydD~uJ0I9DTQ,AAIA--8"),
			new FreenetURI("CHK@H4nfdTqgQUQ0CkdPzvrs2F~IIkjOCnfEn~S042jUxuw,wkCrKtmvmYQzuo3f4v2JlB87wJkK0dspmGJ~ivztYP8,AAIA--8")
		};

		WoTOwnMessageList messageList = new WoTOwnMessageList(myAuthor, 123);
		messageList.initializeTransient(db, mMessageManager);
		messageList.storeWithoutCommit();
		db.commit();
		
		mMessageListID = messageList.getID();
		
		for(int i = 0; i < messages.length; ++i) {
			mMessageManager.onOwnMessageInserted(messages[i].getID(), messageURIs[i]);
		}
		
		for(WoTOwnMessage message : messages) {
			for(Board board : message.getBoards()) 
				mMessageBoards.put(message.getID(), board.getName());
		}
	
		
		mHardcodedEncodedMessageList = new String(
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + 
				"<Freetalk-testing>" + 
				"<MessageList Version=\"1\">" + 
				"<Message Date=\"2009-06-01\" ID=\"" + messages[0].getID() + "\" URI=\"CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8\">" + 
				"<Board Name=\"en.board1\"/>" + 
				"<Board Name=\"en.board2\"/>" + 
				"</Message>" + 
				"<Message Date=\"2008-05-02\" ID=\"" + messages[1].getID() + "\" URI=\"CHK@0YUT4BEorqJCETQrLSgHBcw5RL7KQNm6Fbpo3ThzTy4,6RzUH23~TwPQ0IDQcgPoxEYX7yBTgTNydD~uJ0I9DTQ,AAIA--8\">" + 
				"<Board Name=\"en.board3\"/>" + 
				"<Board Name=\"en.board4\"/>" + 
				"</Message>" + 
				"<Message Date=\"2007-04-03\" ID=\"" + messages[2].getID() + "\" URI=\"CHK@H4nfdTqgQUQ0CkdPzvrs2F~IIkjOCnfEn~S042jUxuw,wkCrKtmvmYQzuo3f4v2JlB87wJkK0dspmGJ~ivztYP8,AAIA--8\">" + 
				"<Board Name=\"en.board5\"/>" + 
				"<Board Name=\"en.board6\"/>" + 
				"</Message>" + 
				"</MessageList>" + 
				"</Freetalk-testing>"
				);
	}

	public void testEncode() throws TransformerException, ParserConfigurationException, NoSuchMessageException, NoSuchMessageListException {
		ByteArrayOutputStream encodedMessageList = new ByteArrayOutputStream(4096);
		
		System.gc(); db.purge(); System.gc();
		
		WoTMessageListXML.encode(mMessageManager, (WoTOwnMessageList)mMessageManager.getOwnMessageList(mMessageListID), encodedMessageList);
		
		assertEquals(mHardcodedEncodedMessageList, encodedMessageList.toString().replaceAll("[\r\n]", ""));
	}

	public void testDecode() throws Exception {
		WoTMessageList decodedList;
		
		{
			ByteArrayInputStream is = new ByteArrayInputStream(mHardcodedEncodedMessageList.getBytes("UTF-8"));
			WoTOwnMessageList messageList = (WoTOwnMessageList)mMessageManager.getOwnMessageList(mMessageListID);
			decodedList = WoTMessageListXML.decode(mMessageManager, messageList.getAuthor(), messageList.getURI(), is);
		}

		System.gc(); db.purge(); System.gc();
		
		/* Now we check every message reference we receive from the decoded XML. For each seen [message, board] pair we remove that
		 * pair from the messageBoards table. If the table is empty at the end, the XML decoding has not dropped any of the pairs */
		for(MessageList.MessageReference ref : decodedList) {
			WoTOwnMessage message = (WoTOwnMessage)mMessageManager.getOwnMessage(ref.getMessageID());
			assertTrue("A board was listed in the message list multiple times: " + ref.getBoard().getName(),
					mMessageBoards.containsElement(message.getID(), ref.getBoard().getName()));
			
			mMessageBoards.removeElement(message.getID(), ref.getBoard().getName());
		}
		
		assertTrue("Not all boards or messages were specified in the message list.", mMessageBoards.isEmpty());
	}

}