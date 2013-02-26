package org.vudroid.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextPaint;
import android.view.*;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.vudroid.core.events.CurrentPageListener;
import org.vudroid.core.events.DecodingProgressListener;
import org.vudroid.core.models.CurrentPageModel;
import org.vudroid.core.models.DecodingProgressModel;
import org.vudroid.core.models.ZoomModel;
import org.vudroid.core.views.PageViewZoomControls;

public abstract class BaseViewerActivity extends Activity implements DecodingProgressListener, CurrentPageListener
{
    private static final int MENU_EXIT = 0;
    private static final int MENU_GOTO = 1;
    private static final int MENU_FULL_SCREEN = 2;
    private static final int DIALOG_GOTO = 0;
    private static final String DOCUMENT_VIEW_STATE_PREFERENCES = "DjvuDocumentViewState";
    private DecodeService decodeService;
    private DocumentView documentView;
    private ViewerPreferences viewerPreferences;
    private Toast pageNumberToast;
    private CurrentPageModel currentPageModel;
    
    public FrontView frontView;
    static Handler handler;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        initDecodeService();
        final ZoomModel zoomModel = new ZoomModel();
        final DecodingProgressModel progressModel = new DecodingProgressModel();
        progressModel.addEventListener(this);
        currentPageModel = new CurrentPageModel();
        currentPageModel.addEventListener(this);
        documentView = new DocumentView(this, zoomModel, progressModel, currentPageModel);
        zoomModel.addEventListener(documentView);
        documentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        decodeService.setContentResolver(getContentResolver());
        decodeService.setContainerView(documentView);
        documentView.setDecodeService(decodeService);
        decodeService.open(getIntent().getData());

        viewerPreferences = new ViewerPreferences(this);

        final FrameLayout frameLayout = createMainContainer();
		frameLayout.addView(documentView);
		
		
		final float scale=getResources().getDisplayMetrics().density;
//		frameLayout.addView(new TextView(this){
//			TextPaint paint;
//			@Override
//			protected void onDraw(Canvas canvas) {
//				super.onDraw(canvas);
//				if(paint==null){
//					paint=new TextPaint();
//					paint.setColor(Color.RED);
//					paint.setTextSize(30);
//				}
//				canvas.drawText("仅测试用", 20, 50, paint);
//			}
//		});
		frontView=new FrontView(BaseViewerActivity.this, documentView);
		
		handler=new Handler(){
			int lastWhat=-1;
			@Override
			public void dispatchMessage(Message msg) {
				if(msg.what==0){
					frontView.init();
					return;
				}
				msg.what=lastWhat*-1;
				switch(msg.what){
				case -1:
					lastWhat=-1;
					if(frameLayout.indexOfChild(frontView)!=-1){
						frameLayout.removeView(frontView);
					}
					TranslateAnimation hideAnim=new TranslateAnimation(0, 0, 0, (int)(60 * scale));
					hideAnim.setDuration(300);
					hideAnim.setFillAfter(true);
					frontView.startAnimation(hideAnim);
					break;
				case 1:
					lastWhat=1;
					if(frameLayout.indexOfChild(frontView)==-1) frameLayout.addView(frontView
							,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, 
									(int)(60 * scale),Gravity.BOTTOM)
					);
					TranslateAnimation showAnim=new TranslateAnimation(0, 0,(int)(60 * scale),  0);
					showAnim.setDuration(300);
					showAnim.setFillAfter(true);
					frontView.startAnimation(showAnim);
					break;
				}
			}
	    };
//        frameLayout.addView(createZoomControls(zoomModel));

        setFullScreen();
        setContentView(frameLayout);

        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        documentView.goToPage(0);
        documentView.showDocument();

        viewerPreferences.addRecent(getIntent().getData());
    }

    public void decodingProgressChanged(final int currentlyDecoding)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS, currentlyDecoding == 0 ? 10000 : currentlyDecoding);
            }
        });
    }
    private int lastPage=0;
    public void currentPageChanged(int pageIndex)
    {
        final String pageText = (pageIndex + 1) + "/" + decodeService.getPageCount();
        if (pageNumberToast != null)
        {
            pageNumberToast.setText(pageText);
        }
        else
        {
            pageNumberToast = Toast.makeText(this, pageText, 300);
        }
        pageNumberToast.setGravity(Gravity.TOP | Gravity.LEFT,0,0);
        pageNumberToast.show();
        saveCurrentPage();
        
        
        if (!documentView.creatingMap) {
			frontView.changeToButton(lastPage, pageIndex);
			lastPage = pageIndex;
		}
    }

    private void setWindowTitle()
    {
        final String name = getIntent().getData().getLastPathSegment();
        getWindow().setTitle(name);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        setWindowTitle();
    }

    private void setFullScreen()
    {
        if (viewerPreferences.isFullScreen())
        {
            getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        else
        {
            getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        }
    }

    private PageViewZoomControls createZoomControls(ZoomModel zoomModel)
    {
        final PageViewZoomControls controls = new PageViewZoomControls(this, zoomModel);
        controls.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        zoomModel.addEventListener(controls);
        controls.setVisibility(View.INVISIBLE);
        return controls;
    }
    private FrontView creatIndexLeftView(){
    	FrontView indexLeftView=new FrontView(this, documentView);
    	
    	return indexLeftView;
    }

    private FrameLayout createMainContainer()
    {
        return new FrameLayout(this);
    }

    private void initDecodeService()
    {
        if (decodeService == null)
        {
            decodeService = createDecodeService();
        }
    }

    protected abstract DecodeService createDecodeService();

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        decodeService.recycle();
        decodeService = null;
        super.onDestroy();
    }

    private void saveCurrentPage()
    {
        final SharedPreferences sharedPreferences = getSharedPreferences(DOCUMENT_VIEW_STATE_PREFERENCES, 0);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getIntent().getData().toString(), documentView.getCurrentPage());
        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	menu.add(0, MENU_EXIT, 0, "退出思科新合作伙伴入门指导手册");
//        menu.add(0, MENU_GOTO, 0, "Go to page");
//        final MenuItem menuItem = menu.add(0, MENU_FULL_SCREEN, 0, "Full screen").setCheckable(true).setChecked(viewerPreferences.isFullScreen());
//        setFullScreenMenuItemText(menuItem);
        return true;
    }

    private void setFullScreenMenuItemText(MenuItem menuItem)
    {
        menuItem.setTitle("Full screen " + (menuItem.isChecked() ? "on" : "off"));
    }

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			new AlertDialog.Builder(this).setTitle("思科新合作伙伴入门指导手册")
					.setMessage("是否退出？")
					.setPositiveButton("确定", new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							System.exit(0);
						}
					}).setNegativeButton("取消", new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						}
					}).create().show();
			return true;
		}else if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
				handler.obtainMessage().sendToTarget();
				return true;
		}
    	
		return super.onKeyDown(keyCode, event);
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case MENU_EXIT:
                System.exit(0);
                return true;
            case MENU_GOTO:
                showDialog(DIALOG_GOTO);
                return true;
            case MENU_FULL_SCREEN:
                item.setChecked(!item.isChecked());
                setFullScreenMenuItemText(item);
                viewerPreferences.setFullScreen(item.isChecked());

                finish();
                startActivity(getIntent());
                return true;
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case DIALOG_GOTO:
                return new GoToPageDialog(this, documentView, decodeService);
        }
        return null;
    }
}
