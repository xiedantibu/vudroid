package org.vudroid.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import cn.com.cisco.pdf.R;

import android.R.color;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View.OnClickListener;

public class FrontView extends HorizontalScrollView implements OnClickListener{
	DocumentView documentView;
	Context context;
	float scale;
	LinearLayout ll;
	static final int myColor=Color.argb(255, 255, 153, 0);
	public FrontView(final Context context,final DocumentView documentView) {
		super(context);
		this.context=context;
		this.documentView=documentView;
		this.setBackgroundResource(R.drawable.frontviewback);
		ll=new LinearLayout(context);
		setHorizontalScrollBarEnabled(false);
//		ll.setBackgroundColor(Color.RED);
		ll.setGravity(Gravity.CENTER);
		scale=getResources().getDisplayMetrics().density;
		addView(ll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT, Gravity.CENTER));
		ll.setPadding((int)(10*scale), 0, (int)(10*scale), 0);
		
	}
	public void init(){
		final int size=documentView.pages.size();
		final ProgressDialog mpDialog=new ProgressDialog(context);
		mpDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);  
        mpDialog.setTitle("提示");  
        mpDialog.setIcon(R.drawable.icon);
        mpDialog.setMessage("正在生成缩略图...");  
        mpDialog.setMax(size);  
        mpDialog.setProgress(0);  
        mpDialog.setIndeterminate(false);
        mpDialog.show();
        mpDialog.setCancelable(false);
        new Thread(){
        	public void run(){
        			for (int i = 1; i <= size; i++) {
        				final int finali=i;
        				final File file=new File(BaseBrowserActivity.getMyPath()+"/"+BaseBrowserActivity.getFileName(),"page"+i+".png");
        				if(!file.exists()){
        					documentView.post(new Runnable() {
    							public void run() {
	        					documentView.goToPage(finali-1);
    							}
    						});
        					while(true){
        						if(file.exists()){
        							break;
        						}
        						try {
									Thread.sleep(100);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
        					}
        				}
        				
        				documentView.post(new Runnable() {
							public void run() {
							try {
								Bitmap bitmap = BitmapFactory
										.decodeStream(new FileInputStream(file));
								ImageButton ib = new ImageButton(context);
								float bitmapScale = 50 * scale
										/ bitmap.getHeight();
								ib.setImageBitmap(Bitmap.createScaledBitmap(
										bitmap,
										(int) (bitmap.getWidth() * bitmapScale),
										(int) (bitmap.getHeight() * bitmapScale),
										false));
								bitmap.recycle();
								ib.setOnClickListener(FrontView.this);
								ib.setTag(finali - 1);
								ib.setBackgroundColor(Color.TRANSPARENT);
								ib.setPadding((int) (3 * scale),
										(int) (5 * scale), (int) (3 * scale),
										(int) (5 * scale));
								ll.addView(ib);
								if (finali == 1) {
									ib.setBackgroundColor(myColor);
								}
								mpDialog.setProgress(finali);
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							}
							}
						});
        			}
					documentView.post(new Runnable() {
						public void run() {
						mpDialog.dismiss();
    					documentView.goToPage(0);
						}
					});
        			documentView.creatingMap=false;
//        		}
        	}
        }.start();
		
	}
	@Override
	public void onClick(View v) {
		if (v instanceof ImageButton) {
			ImageButton ib = (ImageButton) v;
			documentView.zoomModel.setZoom(1);
			documentView.goToPage((Integer) ib.getTag());
			documentView.showDocument();
			clearAllButtonBackground();
			ib.setBackgroundColor(myColor);
		}
	}
	public void clearAllButtonBackground(){
		LinearLayout ll=(LinearLayout) getChildAt(0);
		for(int i=0;i<34;i++){
			ll.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
		}
	}
	public void changeToButton(int lastPage,int page){
		LinearLayout ll=(LinearLayout) getChildAt(0);
		for(int i=0;i<34;i++){
			ll.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
			if(i==page){
				ll.getChildAt(i).setBackgroundColor(myColor);
				scrollToButton(page);
			}
		}
	}
	public void scrollToButton(int page){
		if(page<3) return;
		int scrollFrom=getScrollX();
		int scrollTo=getChildAt(0).getWidth()/34*(page-3);
		scrollXAnim(scrollFrom, scrollTo);
	}
	long animTime=300;
	public void scrollXAnim(final int scrollFrom,final int scrollTo){
		final long timeRec=System.currentTimeMillis();
		new Thread(){
			public void run(){
				while (true) {
					long timeUse=System.currentTimeMillis()-timeRec;
					if(timeUse>animTime) break;
					handler.obtainMessage((int)(scrollFrom+(scrollTo-scrollFrom)*timeUse/animTime)).sendToTarget();
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}.start();
	}
	Handler handler=new Handler(){
		@Override
		public void dispatchMessage(Message msg) {
			scrollTo(msg.what, 0);
		}
	};
}

