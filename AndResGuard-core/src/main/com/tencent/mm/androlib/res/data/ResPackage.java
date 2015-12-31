
package main.com.tencent.mm.androlib.res.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @author shwenzhang
 *
 */
public class ResPackage {
	private final String mName;
	
	private Map<Integer, String> mSpecNamesReplace = new LinkedHashMap<Integer, String>();
	private HashSet<String> mSpecNamesBlock = new HashSet<String>();
//	private Map<Integer, String> mSpecNamesOldBlock = new LinkedHashMap<Integer, String>();

	private boolean mCanProguard = false;
	
	public ResPackage( int id, String name) {
		this.mName = name;
	}
	
	public void setCanProguard(boolean set) {
		mCanProguard = set;
	}
	
	public boolean isCanProguard() {
		return mCanProguard;
	}
	
	public boolean hasSpecRepplace(String resID) {
		return mSpecNamesReplace.containsKey(resID);
	}

	public String getSpecRepplace(int resID) {
		return mSpecNamesReplace.get(resID);
	}
	
	
	public void putSpecNamesReplace(int resID, String value) {
		 mSpecNamesReplace.put(resID, value);
	}
	
	public void putSpecNamesblock(String value) {
		mSpecNamesBlock.add(value);
	}
	
	
	public HashSet<String> getSpecNamesBlock() {
		return mSpecNamesBlock;
	}
	

	public String getName() {
		return mName;
	}

	@Override
	public String toString() {
		return mName;
	}



}
