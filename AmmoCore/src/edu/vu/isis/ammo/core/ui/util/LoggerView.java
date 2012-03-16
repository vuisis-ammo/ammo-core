package edu.vu.isis.ammo.core.ui.util;

import edu.vu.isis.ammo.core.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LoggerView extends BaseAdapter {
	
	private LayoutInflater mInflater;
	private Bitmap mIcon1;

	private int active_view;
	private int last_path;
	public String[][] values = new String[0][];
	private final Resources resources;

	public LoggerView(Context context, String[][] str_arr_values, int int_active_view) {
	    this.resources = context.getResources();
	    mInflater = LayoutInflater.from(context);
	    values = str_arr_values;
	    active_view = int_active_view;
	}

	public Object getItem(int position) {
	    return position;
	}

	public long getItemId(int position) {
	    return position;
	}

	public View getView(int position, View convertView, ViewGroup parent) {

	    int padding = 0;

	    // int selfstate = Integer.parseInt(values[position][4].trim());

	    ViewHolder holder;
	    if (convertView == null) {

	        convertView = mInflater.inflate(R.xml.lumberjack, null);

	        holder = new ViewHolder();
	        holder.text = (TextView) convertView.findViewById(R.id.text_view);
	        holder.icon = (ImageView) convertView.findViewById(R.id.icon_view);
	        holder.expanded = (ImageView) convertView.findViewById(R.id.expanded_view);
	        holder.llout = (LinearLayout) convertView.findViewById(R.id.lumberjack);
	        holder.docs = (TextView) convertView.findViewById(R.id.doc_count_view);
	        holder.prun = (ImageView) convertView.findViewById(R.id.prun_view);
	        holder.coord = (ImageView) convertView.findViewById(R.id.coord_view);
	        holder.button = (Button) convertView.findViewById(R.id.button_view);
	        /*
	        holder.button.setOnClickListener(new OnClickListener() {
	            @Override
	            public void onClick(View arg0) {
	                main.tabHost.setCurrentTab(2);
	            }

	        });
	        convertView.setTag(holder);
	        */
	    } else {

	        holder = (ViewHolder) convertView.getTag();
	    }

	    // Bind the data efficiently with the holder.
	    String text = values[position][0];

	    String docs = values[position][11];
	    if (!docs.equals("0")) {
	        holder.docs.setText(docs);
	        holder.docs.setVisibility(View.VISIBLE);
	    } else {
	        holder.docs.setVisibility(View.INVISIBLE);
	    }
	    holder.coord.setVisibility(values[position][12].equals("") || values[position][12].equals("0") || values[position][12].equals("0.0") ? View.INVISIBLE : View.VISIBLE);
	    holder.prun.setVisibility(values[position][14].equals("") ? View.INVISIBLE : View.VISIBLE);
	    holder.text.setText(text);
	    //holder.text.setTextSize(GlobalVars.Style.TextSize);
	    if (position == active_view) {
	        holder.text.setTextColor(R.color.headline_font);
	        PaintDrawable pd = new PaintDrawable(Color.RED);
	        holder.llout.setBackgroundDrawable(pd);
	        holder.text.setSingleLine(false);
	        holder.button.setVisibility(values[position][1].equals("-1") ? View.GONE : View.VISIBLE);
	    } else {
	        holder.text.setTextColor(R.color.headline_font);

	        PaintDrawable pd = new PaintDrawable(Color.TRANSPARENT);
	        holder.llout.setBackgroundDrawable(pd);
	        holder.text.setSingleLine(true);
	        holder.button.setVisibility(View.GONE);
	    }

	    int ix = Integer.parseInt(values[position][5].trim());
	    this.mIcon1 = BitmapFactory.decodeResource(resources, R.drawable.appl_icon);
	    holder.icon.setImageBitmap(mIcon1);

	    if (values[position][8].equals("-1")) {
	        holder.expanded.setVisibility(View.VISIBLE);
	        holder.expanded.setImageResource(android.R.drawable.ic_input_add);
	    } else if (values[position][8].equals("0")) {
	        holder.expanded.setVisibility(View.INVISIBLE);
	    } else if (values[position][8].equals("1")) {
	        holder.expanded.setVisibility(View.VISIBLE);
	        if (values[position][9].equals("1")) {
	            holder.expanded.setImageResource(android.R.drawable.ic_menu_revert);
	        } else {
	            holder.expanded.setImageResource(android.R.drawable.ic_input_add);
	        }
	    }
	    padding = Integer.parseInt(values[position][7].trim()) * 24;
	    convertView.setPadding(padding, 5, 5, 5);

	    return convertView;
	}

	static class ViewHolder {
	    LinearLayout llout;
	    TextView text;
	    ImageView icon;
	    ImageView expanded;
	    ImageView coord;
	    ImageView prun;
	    TextView docs;
	    Button button;
	}

	@Override
	public int getCount() {
	    // TODO Auto-generated method stub
	    return values.length;
	}

	public String[] getValues(int position) {
	    return values[position];
	}

	public String getValue(int position) {
	    return values[position][0];
	}

	public String getDatid(int position) {
	    return values[position][1];
	}

	public String getPath(int position) {
	    return values[position][2];
	}

	public String getParent(int position) {
	    return values[position][3];
	}

	public void set_active_view() {
	    //active_view = GlobalVars.cn.index;
		this.active_view = 1;
	}

	public int get_active_view() {
	    return active_view;
	}

	public void set_last_path(int position) {
	    last_path = position;
	}

	public int get_last_path() {
	    return last_path;
	}
}