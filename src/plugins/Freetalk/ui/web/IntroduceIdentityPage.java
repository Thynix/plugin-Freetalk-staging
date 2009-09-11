package plugins.Freetalk.ui.web;

import java.util.List;

import plugins.Freetalk.Freetalk;
import plugins.Freetalk.WoT.WoTIdentityManager;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.NoSuchTaskException;
import plugins.Freetalk.tasks.WoT.IntroduceIdentityTask;
import freenet.clients.http.RedirectException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class IntroduceIdentityPage extends WebPageImpl {
	
	protected final String mTaskID;
	
	protected final int mNumberOfPuzzles;
	
	private final WoTIdentityManager mIdentityManager;
	
	public IntroduceIdentityPage(WebInterface myWebInterface, WoTOwnIdentity myViewer, String myTaskID, int numberOfPuzzles) {
		super(myWebInterface, myViewer, null);

		mTaskID = myTaskID;
		
		mIdentityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		mNumberOfPuzzles = numberOfPuzzles;
	}

	public IntroduceIdentityPage(WebInterface myWebInterface, WoTOwnIdentity viewer, HTTPRequest request) throws NoSuchTaskException {
		super(myWebInterface, viewer, request);
		
		mTaskID = mRequest.getPartAsString("TaskID", 64);
		mIdentityManager = (WoTIdentityManager)mFreetalk.getIdentityManager();
		
		if(!request.isPartSet("SolvePuzzles")) {
			// We received an invalid request
			mNumberOfPuzzles = 0;
			return;
		}
		
		synchronized(mFreetalk.getTaskManager()) {

			IntroduceIdentityTask myTask = (IntroduceIdentityTask)mFreetalk.getTaskManager().getTask(mTaskID);

			int idx = 0;

			while(request.isPartSet("PuzzleID" + idx)) {
				String id = request.getPartAsString("PuzzleID" + idx, 128);
				String solution = request.getPartAsString("Solution" + id, 32); /* TODO: replace "32" with the maximal solution length */

				if(!solution.equals("")) {

					try {
						mIdentityManager.solveIntroductionPuzzle((WoTOwnIdentity)mOwnIdentity, id, solution);

						myTask.onPuzzleSolved();
					}
					catch(Exception e) {
						/* The identity or the puzzle might have been deleted here */
						Logger.error(this, "solveIntroductionPuzzle() failed", e);
					}
				}
				++idx;
			}

			myTask.storeAndCommit();
			
			mNumberOfPuzzles = myTask.getNumberOfPuzzlesToSolve();
		}
	}

	public void make() throws RedirectException {
		HTMLNode contentBox = addAlertBox("Introduce your identity");
		
		contentBox.addChild("p", "You have not received enough trust values from other identities: Your messages will not be seen by others." +
				" You have to solve the following puzzles to get trusted by other identities, then your messages will be visible to the most identities: ");
		
		List<String> puzzleIDs = null;
		try {
			puzzleIDs = mIdentityManager.getIntroductionPuzzles((WoTOwnIdentity)mOwnIdentity, mNumberOfPuzzles);
		} catch (Exception e) {
			Logger.error(this, "getIntroductionPuzzles() failed", e);
			
			new ErrorPage(mWebInterface, mOwnIdentity, mRequest, "Obtaining puzzles failed", e.getMessage()).addToPage(contentBox);
			return;
		}
		
		if(puzzleIDs.size() == 0 ) {
			contentBox.addChild("p", "No puzzles were downloaded yet, sorry. Please give the WoT plugin some time to retrieve puzzles.");
			return;
		}
			
		HTMLNode solveForm = mFreetalk.getPluginRespirator().addFormChild(contentBox, Freetalk.PLUGIN_URI + "/IntroduceIdentity", "SolvePuzzles");

		solveForm.addChild("input", new String[] { "type", "name", "value", }, new String[] { "hidden", "TaskID", mTaskID });

		int counter = 0;
		for(String puzzleID : puzzleIDs) {
			// Display as much puzzles per row as fitting in the browser-window via "inline-block" style. Nice, eh?
			HTMLNode cell = solveForm.addChild("div", new String[] { "align" , "style"}, new String[] { "center" , "display: inline-block"});

			cell.addChild("img", "src", Freetalk.PLUGIN_URI + "/GetPuzzle?PuzzleID=" + puzzleID); 
			cell.addChild("br");
			cell.addChild("input", new String[] { "type", "name", "value", }, new String[] { "hidden", "PuzzleID" + counter, puzzleID });
			cell.addChild("input", new String[] { "type", "name", "size"}, new String[] { "text", "Solution" + puzzleID, "10" });

			++counter;
		}

		solveForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "Solve", "Submit" });
	}

}