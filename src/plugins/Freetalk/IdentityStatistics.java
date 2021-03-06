/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import plugins.Freetalk.MessageList.MessageListID;
import plugins.Freetalk.exceptions.NoSuchMessageListException;
import freenet.support.Logger;

/**
 * This class stores statistics about a single identity.
 * It is used for {@link MessageList} and {@link Message} download decisions.
 * It also speeds up Freetalk by caching the results of complex database queries.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class IdentityStatistics extends Persistent {
	
	@IndexedField
	private final Identity mIdentity;

	/**
	 * The index of the latest message list which is available for this identity.
	 */
	private long mHighestFetchedMessageListIndex = -1;
	
	/**
	 * The lowest index of which the message list is fetched and all message lists after that index are fetched as well.
	 */
	private long mLowestFetchedMessageListIndex = -1;
	
	
	protected IdentityStatistics(final Identity myIdentity) {
		mIdentity = myIdentity;
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(2);
		
		if(mIdentity == null)
			throw new NullPointerException("mIdentity==null");
		
		if((mLowestFetchedMessageListIndex == -1) ^ (mHighestFetchedMessageListIndex == -1))
			throw new IllegalStateException("mLowestFetchedMessageListIndex==" + mLowestFetchedMessageListIndex 
					+ "; mHighestFetchedMessageListIndex==" + mHighestFetchedMessageListIndex);
		
		if(mLowestFetchedMessageListIndex >= 0) {
			final MessageManager messageManager = mFreetalk.getMessageManager();
			
			for(long i = mLowestFetchedMessageListIndex; i <= mHighestFetchedMessageListIndex; ++i) {
				try {
					messageManager.getMessageList(MessageListID.construct(mIdentity, i).toString());
				} catch(NoSuchMessageListException e) {
					throw new IllegalStateException("Missing index " + i + "; mLowestFetchedMessageListIndex==" + mLowestFetchedMessageListIndex 
							+ "; mHighestFetchedMessageListIndex==" + mHighestFetchedMessageListIndex);
				}
			}
		}
		
		// TODO: If lowest==highest==-1, check whether index 0 is available.. if it is, they should be 0 at least...
	}
	
	public final Identity getIdentity() {
		if(mIdentity instanceof Persistent) ((Persistent)mIdentity).initializeTransient(mFreetalk);
		return mIdentity;
	}
	

	/**
	 * Get the the message list index number such that all message lists with index x are fetched for:<br>
	 * <code>getIndexOfOldestAvailableMessageList() <= x <= getIndexOfLatestAvailableMessageList()</code><br><br>
	 * 
	 * In other words: It is guaranteed that no slot between the latest and oldest reported index is empty.
	 * If there is a slot missing, the statistics object tries to chose the latest/lowest numbers biased towards the higher available
	 * fragment instead of the lower one.
	 * 
	 * It is NOT guaranteed that all message lists below the returned number are not fetched, some might exist in the database already!
	 * - The numbers returned by this function are supposed to be used for message list fetching, its not bad if we fetch the same message list
	 * twice.
	 */
	public final synchronized long getIndexOfLatestAvailableMessageList() throws NoSuchMessageListException {
		if(mHighestFetchedMessageListIndex == -1)
			throw new NoSuchMessageListException(null);
		
		return mHighestFetchedMessageListIndex;
	}
	
	private final synchronized void setIndexOfLatestAvailableMessageList(long index) {
		if(index < mLowestFetchedMessageListIndex)
			throw new RuntimeException("Illegal index: "+ index);
		
		mHighestFetchedMessageListIndex = index;
		if(mLowestFetchedMessageListIndex == -1) mLowestFetchedMessageListIndex = index;
	}
	
	/**
	 * Get the the message list index number such that all message lists with index x are fetched for:<br>
	 * <code>getIndexOfOldestAvailableMessageList() <= x <= getIndexOfLatestAvailableMessageList()</code><br><br>
	 * 
	 * In other words: It is guaranteed that no slot between the latest and oldest reported index is empty.
	 * If there is a slot missing, the statistics object tries to chose the latest/lowest numbers biased towards the higher available
	 * fragment instead of the lower one.
	 * 
	 * It is NOT guaranteed that all message lists below the returned number are not fetched, some might exist in the database already!
	 * - The numbers returned by this function are supposed to be used for message list fetching, its not bad if we fetch the same message list
	 * twice.
	 */
	public final synchronized long getIndexOfOldestAvailableMessageList() throws NoSuchMessageListException {
		if(mLowestFetchedMessageListIndex == -1)
			throw new NoSuchMessageListException(null);
		
		return mLowestFetchedMessageListIndex;
	}
	
	private final synchronized void setIndexOfOldestAvailableMessageList(long index) {
		if(index > mHighestFetchedMessageListIndex)
			throw new RuntimeException("Illegal index: " + index);
		
		mLowestFetchedMessageListIndex = index;
		if(mHighestFetchedMessageListIndex == -1) mHighestFetchedMessageListIndex = index;
	}
	
	private final void expandHighestAvailableMessageListIndex() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		// TODO: Optimization: We should observe the typical amount of lists which are skipped by this function. If it is high then it 
		// might be faster to query a sorted set of the message lists with higher index instead of skipping them one by one....
		
		long higherIndex;
		
		do {	
			higherIndex = mHighestFetchedMessageListIndex+1;
			
			try {
				messageManager.getMessageList(MessageListID.construct(mIdentity, higherIndex).toString());
				mHighestFetchedMessageListIndex = higherIndex;
			} catch(NoSuchMessageListException e) { }
			
		} while(mHighestFetchedMessageListIndex == higherIndex);
		
		assert(messageListIndicesAreValid());
	}
	
	private final void expandLowestAvailableMessageListIndex() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		// TODO: Optimization: We should observe the typical amount of lists which are skipped by this function. If it is high then it 
		// might be faster to query a sorted set of the message lists with higher index instead of skipping them one by one....
		
		long lowerIndex;
		
		do {
			lowerIndex = mLowestFetchedMessageListIndex-1;
			if(lowerIndex < 0)
				break;
			
			try {
				messageManager.getMessageList(MessageListID.construct(mIdentity, lowerIndex).toString());
				mLowestFetchedMessageListIndex = lowerIndex;
			} catch(NoSuchMessageListException e) { }
			
		} while(mLowestFetchedMessageListIndex == lowerIndex);
		
		assert(messageListIndicesAreValid());
	}
	
	// TODO: This is public since we need it in WoTMessageManager. It could be made private if WoTMessageManager.onMessageListFetchFailed
	// called a method of MessageManager for calling this function instead of calling it directly.
	public final void onMessageListFetched(final MessageList messageList) {
		if(messageList instanceof OwnMessageList)
			throw new RuntimeException("OwnMessageList are not allowed: " + messageList);
		
		final long newIndex = messageList.getIndex();
		
		if(newIndex >= mLowestFetchedMessageListIndex && newIndex <= mHighestFetchedMessageListIndex) {
			Logger.warning(this, "MessageList was fetched already: " + messageList);
			return;
		}
		
		if(newIndex > mHighestFetchedMessageListIndex+1) {
			// When the list of fetched indices is fragmented, there are two possible biases which this function can have:
			// Set its boundaries to the lowest available fragment or to the highest available fragment. 
			// - We set the boundaries to the highest available fragment so the message list fetcher fetches new message lists fast.
			setIndexOfLatestAvailableMessageList(newIndex);
			setIndexOfOldestAvailableMessageList(newIndex);
			assert(messageListIndicesAreValid());
			expandHighestAvailableMessageListIndex();
			expandLowestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex == mHighestFetchedMessageListIndex+1) {
			setIndexOfLatestAvailableMessageList(newIndex);
			assert(messageListIndicesAreValid());
			expandHighestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex == mLowestFetchedMessageListIndex-1) {
			setIndexOfOldestAvailableMessageList(newIndex);
			assert(messageListIndicesAreValid());
			expandLowestAvailableMessageListIndex();
			return;
		}
		
		if(newIndex < mLowestFetchedMessageListIndex-1) {
			// The mLowestFetchedMessageListIndex-1 index will definitely not be available, no need to check
			// expandLowestAvailableMessageListIndex();
			return;
		}
		
		// This should not happen:
		throw new RuntimeException("Unhandled case: lowest index=" + mLowestFetchedMessageListIndex + "; "
				+ "highest index=" + mHighestFetchedMessageListIndex + "; "
				+ "new index=" + newIndex);
	}
	
	protected final void onMessageListDeleted(final MessageList messageList) {
		if(messageList instanceof OwnMessageList)
			throw new RuntimeException("OwnMessageList are not allowed: " + messageList);
		
		final long deletedIndex = messageList.getIndex();
		
		if(deletedIndex < mLowestFetchedMessageListIndex || deletedIndex > mHighestFetchedMessageListIndex)
			return;
		
		if(mLowestFetchedMessageListIndex == mHighestFetchedMessageListIndex) {
			// TODO: Optimization: If this happens often and in a specific order during identity deletion it might cause O(n*n)...
			// Check whether we need to do something against that.
			setIndexOfOldestAvailableMessageList(-1);
			setIndexOfLatestAvailableMessageList(-1);
			assert(messageListIndicesAreValid());
			expandHighestAvailableMessageListIndex();
			return;
		}

		if(deletedIndex == mHighestFetchedMessageListIndex) {
			setIndexOfLatestAvailableMessageList(deletedIndex-1);
			assert(messageListIndicesAreValid());
			return;
		}
		
		if(deletedIndex >= mLowestFetchedMessageListIndex) {
			setIndexOfOldestAvailableMessageList(deletedIndex+1);
			assert(messageListIndicesAreValid());
			return;
		}
	
		// This should not happen:
		throw new RuntimeException("Unhandled case: lowest index=" + mLowestFetchedMessageListIndex + "; "
				+ "highest index=" + mHighestFetchedMessageListIndex + "; "
				+ "deleted index=" + deletedIndex);
	}
	
	private final boolean messageListIndicesAreValid() {
		final MessageManager messageManager = mFreetalk.getMessageManager();
		
		if((mLowestFetchedMessageListIndex == -1) && (mHighestFetchedMessageListIndex == -1))
			return true;
		
		if((mLowestFetchedMessageListIndex == -1) ^ (mHighestFetchedMessageListIndex == -1)) {
			// Does not make sense to have only one of them be -1
			Logger.error(this, "Wrong index numbers found: lowest==" + mLowestFetchedMessageListIndex + 
					"; highest==" + mHighestFetchedMessageListIndex);
			return false;
		}
		
		for(long i = mLowestFetchedMessageListIndex; i <= mHighestFetchedMessageListIndex; ++i) {
			try  {
				messageManager.getMessageList(MessageListID.construct(mIdentity, i).toString());
			} catch(NoSuchMessageListException e) {
				Logger.error(this, "Wrong index numbers found: lowest==" + mLowestFetchedMessageListIndex + 
						"; highest==" + mHighestFetchedMessageListIndex);
				return false;
			}
		}
		
		return true;
	}

	// TODO: This is public since we need it in WoTMessageManager. It could be made private if WoTMessageManager.onMessageListFetchFailed
	// called a method of MessageManager for calling this function instead of calling it directly.
	public final void storeWithoutCommit() {
		try {		
			// 2 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(2);
			
			throwIfNotStored(mIdentity);
			
			Logger.debug(this, "Storing for " + getIdentity() + " with mLowestFetchedMessageListIndex == " + mLowestFetchedMessageListIndex
					+ "; mHighestFetchedMessageListIndex == " + mHighestFetchedMessageListIndex);
			
			checkedStore();
		}
		catch(final RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

}
