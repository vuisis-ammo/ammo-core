package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;

import edu.vu.isis.ammo.util.Tree;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class TreeAdapter<T> extends BaseAdapter {

	private LayoutInflater mInflater;
	private Tree<T> mObjects;
	private ArrayList<T> objList;
	private Context mContext;
	
	private int mResource;
	private int textViewId;
	
	private int leftPadding;
	private int topPadding;
	private int rightPadding;
	private int bottomPadding;
	
	
	public TreeAdapter(Tree<T> objects, Context context, int resource,
			int textViewResourceId) {
		
		refill(objects);
		
		mContext = context;
		textViewId = textViewResourceId;
		mResource = resource;
		mInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		leftPadding = 20;
		topPadding = rightPadding = bottomPadding = 5;
	}
	
	public void refill(Tree<T> objects) {
		
		mObjects = objects;
		
		objList = new ArrayList<T>();
		objList.add(objects.getHead());
		objList = buildObjectList(objects, objects.getHead(), objList);
		
	}

	@Override
	public int getCount() {
		return objList.size();
	}
	
	public Context getContext() {
        return mContext;
    }

	@Override
	public T getItem(int position) {
        return objList.get(position);
    }

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public void setLeftPadding(int padding) {
		leftPadding = padding;
	}
	
	public void setRightPadding(int padding) {
		rightPadding = padding;
	}
	
	public void setTopPadding(int padding) {
		topPadding = padding;
	}
	
	public void setBottomPadding(int padding) {
		bottomPadding = padding;
	}

	private ArrayList<T> buildObjectList(Tree<T> tree, T head, ArrayList<T> arr) {
		ArrayList<T> successorList = (ArrayList<T>)tree.getSuccessors(head);
		if(successorList == null) {
			return arr;
		} else {
			for(T successor : successorList) {
				Tree<T> subTree = tree.getTree(successor);
				arr.add(successor);
				buildObjectList(subTree, successor, arr);
			}
			return arr;
		}
		
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup group) {
		
		View view;
		TextView text;
		
		if (convertView == null) {
            view = mInflater.inflate(mResource, group, false);
        } else {
            view = convertView;
        }

		try {
			text = (TextView) view.findViewById(textViewId);
		} catch (ClassCastException e) {
			Log.e("TreeAdapter",
					"You must supply a resource ID for a TextView");
			throw new IllegalStateException(
					"TreeAdapter requires the resource ID to be a TextView", e);
		}
		
		int nestLevel = getNestLevel(mObjects.getTree(objList.get(position)), 0);

		T item = getItem(position);
        if (item instanceof CharSequence) {
            text.setText((CharSequence)item);
        } else {
            text.setText(item.toString());
        }

		view.setPadding(leftPadding * nestLevel, topPadding, rightPadding,
				bottomPadding);
		
        return view;
		
	}
	
	private int getNestLevel(Tree<T> tree, int nestLvl) {
		Tree<T> parent = tree.getParent();
		if(parent == null) {
			return nestLvl;
		} else {
			return getNestLevel(parent, nestLvl+1);
		}
	}
	

}
