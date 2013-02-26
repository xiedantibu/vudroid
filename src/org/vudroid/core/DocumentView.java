package org.vudroid.core;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;
import android.widget.Toast;

import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.CurrentPageModel;
import org.vudroid.core.models.DecodingProgressModel;
import org.vudroid.core.models.ZoomModel;
import org.vudroid.core.multitouch.MultiTouchZoom;
import org.vudroid.core.multitouch.MultiTouchZoomImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DocumentView extends View implements ZoomListener {
    final ZoomModel zoomModel;
    private final CurrentPageModel currentPageModel;
    DecodeService decodeService;
    final HashMap<Integer, Page> pages = new HashMap<Integer, Page>();
    private boolean isInitialized = false;
    private int pageToGoTo;
    private float lastX;
    private float lastY;
    private VelocityTracker velocityTracker;
    private final Scroller scroller;
    DecodingProgressModel progressModel;
    private RectF viewRect;
    private boolean inZoom;
    private long lastDownEventTime;
    private static final int DOUBLE_TAP_TIME = 500;
    private MultiTouchZoom multiTouchZoom;
    private Context context;
    
    public DocumentView(Context context, final ZoomModel zoomModel, DecodingProgressModel progressModel, CurrentPageModel currentPageModel) {
        super(context);
        this.context=context;
        this.zoomModel = zoomModel;
        this.progressModel = progressModel;
        this.currentPageModel = currentPageModel;
        setKeepScreenOn(true);
        scroller = new Scroller(getContext());
        setFocusable(true);
        setFocusableInTouchMode(true);
        initMultiTouchZoomIfAvailable(zoomModel);
        
    }

    private void initMultiTouchZoomIfAvailable(ZoomModel zoomModel) {
        try {
            multiTouchZoom = (MultiTouchZoom) Class.forName("org.vudroid.core.multitouch.MultiTouchZoomImpl").getConstructor(ZoomModel.class).newInstance(zoomModel);
        } catch (Exception e) {
            System.out.println("Multi touch zoom is not available: " + e);
        }
    }

    public void setDecodeService(DecodeService decodeService) {
        this.decodeService = decodeService;
    }

    private void init() {
        if (isInitialized) {
            return;
        }
        final int width = decodeService.getEffectivePagesWidth();
        final int height = decodeService.getEffectivePagesHeight();
        for (int i = 0; i < decodeService.getPageCount(); i++) {
            pages.put(i, new Page(this, i));
            pages.get(i).setAspectRatio(width, height);
        }
        isInitialized = true;
        invalidatePageSizes();
        goToPageImpl(pageToGoTo);
        
        BaseViewerActivity.handler.sendEmptyMessage(0);
    }

    private void goToPageImpl(final int toPage) {
        scrollTo(0, pages.get(toPage).getTop()+1);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        // bounds could be not updated
        post(new Runnable() {
            public void run() {
                currentPageModel.setCurrentPageIndex(getCurrentPage());
            }
        });
        if (inZoom) {
            return;
        }
        // on scrollChanged can be called from scrollTo just after new layout applied so we should wait for relayout
        post(new Runnable() {
            public void run() {
                updatePageVisibility();
            }
        });
    }

    private void updatePageVisibility() {
        for (Page page : pages.values()) {
            page.updateVisibility();
        }
    }

    public void commitZoom() {
        for (Page page : pages.values()) {
            page.invalidate();
        }
        inZoom = false;
    }

    public void showDocument() {
        // use post to ensure that document view has width and height before decoding begin
        post(new Runnable() {
            public void run() {
                init();
                updatePageVisibility();
            }
        });
    }

    public void goToPage(int toPage) {
        if (isInitialized) {
            goToPageImpl(toPage);
        } else {
            pageToGoTo = toPage;
        }
    }

    public int getCurrentPage() {
        for (Map.Entry<Integer, Page> entry : pages.entrySet()) {
            if (entry.getValue().isVisible()) {
                return entry.getKey();
            }
        }
        return 0;
    }

    public void zoomChanged(float newZoom, float oldZoom) {
        inZoom = true;
        stopScroller();
        final float ratio = newZoom / oldZoom;
        invalidatePageSizes();
        scrollTo((int) ((getScrollX() + getWidth() / 2) * ratio - getWidth() / 2), (int) ((getScrollY() + getHeight() / 2) * ratio - getHeight() / 2));
        postInvalidate();
    }
    public boolean creatingMap=true;
	public void saveBitmapToSdcard(Bitmap bitmap,int pageindex){
		float screenWidth=getResources().getDisplayMetrics().widthPixels;
		float bitmapScale = screenWidth / 5 / 2 / bitmap.getWidth();
		Bitmap outBitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()*bitmapScale), (int)(bitmap.getHeight()*bitmapScale), false);
//		bitmap.recycle();
		try {
			String status = Environment.getExternalStorageState();
			if (status.equals(Environment.MEDIA_MOUNTED)) {
				String tempPath = BaseBrowserActivity.getMyPath() + "/"
						+ BaseBrowserActivity.getFileName();
				File destDir = new File(tempPath);
				if (!destDir.exists()) {
					destDir.mkdirs();
				}
				File file = new File(tempPath, "page"+pageindex + ".png");
				if (!file.exists()) {
					FileOutputStream out = new FileOutputStream(file);
					outBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
					out.flush();
					out.close();
				}
			}
		} catch (Exception e) {
			Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
	static public Bitmap convertPageToBitmap(Page page){
		Bitmap bitmap=Bitmap.createBitmap((int)(page.bounds.width()), (int)(page.bounds.height()), Bitmap.Config.RGB_565);
		Canvas canvas=new Canvas(bitmap);
		canvas.translate(0, -page.bounds.height()*page.index);
		page.draw(canvas);
		return bitmap;
	}
	public void convertAndSave(Page page){
		saveBitmapToSdcard(convertPageToBitmap(page),page.index+1);
	}
    private void dealMyTouch(MotionEvent ev){
    	float touchScale=800f/context.getResources().getDisplayMetrics().widthPixels;
    	float x=ev.getX()*touchScale;
    	float y=ev.getY()*touchScale;
    	float zoom=zoomModel.getZoom();
        float evPageX=(getScrollX()+x)/zoom;
        float evPageY=(getScrollY()+y)/zoom;
        float realYInTouchPage = 0;
        int realTouchPage=0;
        for(int i=0;i<pages.size();i++){
        	if(pages.get(i).getTop()/zoom<evPageY){
        		if(i==pages.size()-1){
        			realYInTouchPage=evPageY-pages.get(i).getTop()/zoom;
        			realTouchPage=i;
        			break;
        		}
            	if(pages.get(i+1).getTop()/zoom>evPageY){
        			realYInTouchPage=evPageY-pages.get(i).getTop()/zoom;
        			realTouchPage=i;
        			break;
            	}
        	}
        }
//    	Log.d("fax", "realX,Y:("+evPageX+","+realYInTouchPage+"),touchPage:"+realTouchPage);
    	if(realTouchPage!=0&&realTouchPage!=pages.size()){
    		if(realYInTouchPage>=40&&realYInTouchPage<=80){
    			if(evPageX>36&&evPageX<158){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(4-1);
    				showDocument();
    			}
    			else if(evPageX>158&&evPageX<278){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(12-1);
    				showDocument();
    			}
    			else if(evPageX>278&&evPageX<392){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(18-1);
    				showDocument();
    			}
    			else if(evPageX>392&&evPageX<522){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(21-1);
    				showDocument();
    			}
    			else if(evPageX>522&&evPageX<643){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(25-1);
    				showDocument();
    			}
    			else if(evPageX>643&&evPageX<763){
    				if(zoom!=1) zoomModel.setZoom(1);
    				goToPageImpl(32-1);
    				showDocument();
    			}
    			return;
    		}
    	}
    	if(!dealWebLinkTouch(realTouchPage, realYInTouchPage, evPageX)){
    		BaseViewerActivity.handler.sendEmptyMessage(1);
    	}
    }
    private boolean dealWebLinkTouch(int realTouchPage,float realYInTouchPage,float evPageX){
    	switch (realTouchPage+1){
    	case 4:
    		if(Math.abs(realYInTouchPage-310)<20&&evPageX>270&&evPageX<340) {
    			return openAWebLink("https://tools.cisco.com/RPF/register/register.do");
    		}else if(Math.abs(realYInTouchPage-333)<20&&evPageX>490&&evPageX<560) {
    			return openAWebLink("http://www.cisco.com/go/partneraccess/");
    		}else if(Math.abs(realYInTouchPage-372)<20&&evPageX>240&&evPageX<323) {
    			return openAWebLink("http://apps.cisco.com/WWChannels/GETLOG/login.do");
    		}else if(Math.abs(realYInTouchPage-395)<20&&evPageX>252&&evPageX<375) {
    			return openAWebLink("http://www.cisco.com/cgi-bin/marketplace/welcome.pl?KEYCODE=cppplaque&STORE_ID=CISCO_COLLATERAL&PRODUCT_ID=16471");
    		}else if(Math.abs(realYInTouchPage-395)<20&&evPageX>400&&evPageX<500) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/market/partner-marks.html");
    		}else if(Math.abs(realYInTouchPage-420)<20&&evPageX>324&&evPageX<406) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/incentives_and_promotions/index.html");
    		}else if(Math.abs(realYInTouchPage-420)<20&&evPageX>467&&evPageX<610) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/index.html");
    		}else if(Math.abs(realYInTouchPage-420)<20&&evPageX>650&&evPageX<720) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/news/news_index.html");
    		}else if(Math.abs(realYInTouchPage-434)<20&&evPageX>211&&evPageX<275) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/news/news_index.html");
    		}else if(Math.abs(realYInTouchPage-460)<20&&evPageX>465&&evPageX<621) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/partner_incentive_fy06.html");
    		}else if(Math.abs(realYInTouchPage-497)<20&&evPageX>380&&evPageX<486) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/tools/tools_finder/partner_finder_center.html");
    		}
    		break;
    	case 5:
    		if(Math.abs(realYInTouchPage-129)<20&&evPageX>507&&evPageX<570) {
    			return openAWebLink("http://www.cisco.com/web/CN/index.html");
    		}else if(Math.abs(realYInTouchPage-167)<20&&evPageX>650&&evPageX<715) {
    			return openAWebLink("http://ccuc.ambow.net/inv/100City_course.html");
    		}else if(Math.abs(realYInTouchPage-180)<20&&evPageX>212&&evPageX<385) {
    			return openAWebLink("http://www.cisco.com/go/pec");
    		}else if(Math.abs(realYInTouchPage-218)<20&&evPageX>234&&evPageX<353) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/index.html");
    		}else if(Math.abs(realYInTouchPage-243)<20&&evPageX>340&&evPageX<400) {
    			return openAWebLink("http://www.cisco.com/web/partners/sell/smb/tools_and_resources/validated_commercial_solutions.html");
    		}else if(Math.abs(realYInTouchPage-282)<20&&evPageX>236&&evPageX<333) {
    			return openAWebLink("http://www.xiaotong.com.cn");
    		}else if(Math.abs(realYInTouchPage-282)<20&&evPageX>373&&evPageX<495) {
    			return openAWebLink("http://www.ciscostation.com.cn");
    		}else if(Math.abs(realYInTouchPage-298)<20&&evPageX>236&&evPageX<317) {
    			return openAWebLink("http://www.imcisco.com");
    		}else if(Math.abs(realYInTouchPage-298)<20&&evPageX>378&&evPageX<469) {
    			return openAWebLink("http://www.syn-cisco.com");
    		}else if(Math.abs(realYInTouchPage-312)<20&&evPageX>236&&evPageX<340) {
    			return openAWebLink("http://www.foundertech.com");
    		}else if(Math.abs(realYInTouchPage-407)<20&&evPageX>231&&evPageX<393) {
    			return openAWebLink("http://www.cisco.com/go/cn/iba");
    		}else if(Math.abs(realYInTouchPage-430)<20&&evPageX>231&&evPageX<292) {
    			return openAWebLink("http://www.cisco.com/go/qpt");
    		}else if(Math.abs(realYInTouchPage-455)<20&&evPageX>463&&evPageX<526) {
    			return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/resale_program.html");
    		}else if(Math.abs(realYInTouchPage-483)<20&&evPageX>263&&evPageX<303) {
    			return openAWebLink("http://tools.cisco.com/s2s/HomePage.do?method=browseHomePage");
    		}else if(Math.abs(realYInTouchPage-508)<20&&evPageX>440&&evPageX<540) {
    			return openAWebLink("http://www.ciscopartnermarketing.com/");
    		}
    		break;
    	case 6:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>223&&evPageX<324) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/incentives_and_promotions/index.html");
			}else if(Math.abs(realYInTouchPage-129)<20&&evPageX>222&&evPageX<300) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/helpline/index.html");
			}else if(Math.abs(realYInTouchPage-154)<20&&evPageX>231&&evPageX<292) {
				return openAWebLink("http://pled.cisco-club.com.cn/");
			}else if(Math.abs(realYInTouchPage-177)<20&&evPageX>460&&evPageX<600) {
				return openAWebLink("http://www.cisco.com/web/CN/solutions/industry/segment_sol/smb/promotions/gain_the_cisco_edge.html?keyCode=000052005");
			}
	    	break;
    	case 7:
    		if(Math.abs(realYInTouchPage-248)<20&&evPageX>284&&evPageX<386) {
				return openAWebLink("http://www.cisco.com/go/pss");
			}else if(Math.abs(realYInTouchPage-280)<20&&evPageX>245&&evPageX<396) {
				return openAWebLink("http://www.cisco.com/go/pss");
			}else if(Math.abs(realYInTouchPage-324)<20&&evPageX>223&&evPageX<306) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/index_log.html");
			}else if(Math.abs(realYInTouchPage-354)<20&&evPageX>359&&evPageX<469) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/index.html");
			}else if(Math.abs(realYInTouchPage-370)<20&&evPageX>227&&evPageX<317) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/sell/technology/index.html");
			}else if(Math.abs(realYInTouchPage-399)<20&&evPageX>232&&evPageX<296) {
//				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-413)<20&&evPageX>682&&evPageX<723) {
				return openAWebLink("http://www.cisco.com/go/guide");
			}else if(Math.abs(realYInTouchPage-428)<20&&evPageX>215&&evPageX<355) {
				return openAWebLink("http://www.cisco.com/go/guide");
			}else if(Math.abs(realYInTouchPage-429)<20&&evPageX>408&&evPageX<587) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/solutions/index.html#~overview");
			}else if(Math.abs(realYInTouchPage-458)<20&&evPageX>276&&evPageX<385) {
//				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-474)<20&&evPageX>607&&evPageX<709) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/news/news_index.html");
			}else if(Math.abs(realYInTouchPage-504)<20&&evPageX>236&&evPageX<287) {
//				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-534)<20&&evPageX>417&&evPageX<488) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/resale_program.html");
			}else if(Math.abs(realYInTouchPage-563)<20&&evPageX>236&&evPageX<296) {
				return openAWebLink("http://www.cisco.com/go/cn/iba");
			}
	    	break;
    	case 8:
    		if(Math.abs(realYInTouchPage-128)<20&&evPageX>235&&evPageX<277) {
				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-143)<20&&evPageX>215&&evPageX<338) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/incentives_and_promotions/index.html");
			}else if(Math.abs(realYInTouchPage-188)<20&&evPageX>215&&evPageX<386) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/partner_incentive_aip.html");
			}else if(Math.abs(realYInTouchPage-264)<20&&evPageX>255&&evPageX<303) {
				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-280)<20&&evPageX>243&&evPageX<340) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/fast_track.html");
			}else if(Math.abs(realYInTouchPage-340)<20&&evPageX>244&&evPageX<334) {
				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-340)<20&&evPageX>346&&evPageX<462) {
				return openAWebLink("");
			}else if(Math.abs(realYInTouchPage-370)<20&&evPageX>214&&evPageX<366) {
				return openAWebLink("http://www.cisco.com/web/partners/incentives_and_promotions/sip.html");
			}
	    	break;
    	case 9:
    		if(Math.abs(realYInTouchPage-142)<20&&evPageX>214&&evPageX<317) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/tools/tools_finder/partner_finder_center.html");
			}else if(Math.abs(realYInTouchPage-203)<20&&evPageX>246&&evPageX<306) {
				return openAWebLink("http://www.cisco.com/cisco/web/CN/support/index.html");
			}else if(Math.abs(realYInTouchPage-309)<20&&evPageX>578&&evPageX<706) {
				return openAWebLink("https://www.myciscocommunity.com/community/partner/smallmediumbusiness");
			}else if(Math.abs(realYInTouchPage-338)<20&&evPageX>246&&evPageX<332) {
				return openAWebLink("http://pled.cisco-club.com.cn/memcp.php");
			}else if(Math.abs(realYInTouchPage-354)<20&&evPageX>225&&evPageX<309) {
				return openAWebLink("http://www.cisco-club.com.cn/");
			}else if(Math.abs(realYInTouchPage-399)<20&&evPageX>214&&evPageX<357) {
				return openAWebLink("http://www.cisco.com/go/pec");
			}else if(Math.abs(realYInTouchPage-414)<20&&evPageX>214&&evPageX<276) {
				return openAWebLink("http://ccuc.ambow.net/inv/100City_course.html");
			}
	    	break;
    	case 10:
    		if(Math.abs(realYInTouchPage-143)<20&&evPageX>214&&evPageX<291) {
				return openAWebLink("http://www.cisco.com/web/CN/ordering/ciscocapital/index.html");
			}else if(Math.abs(realYInTouchPage-233)<20&&evPageX>579&&evPageX<723) {
				return openAWebLink("http://service.ciscochannel.com.cn/index.jsp?soft=true");
			}else if(Math.abs(realYInTouchPage-278)<20&&evPageX>245&&evPageX<522) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/partner_incentive_fy06.html");
			}else if(Math.abs(realYInTouchPage-292)<20&&evPageX>510&&evPageX<708) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/partner_incentive_fy06.html");
			}
	    	break;
    	case 11:
    		if(Math.abs(realYInTouchPage-141)<20&&evPageX>661&&evPageX<722) {
				return openAWebLink("http://www.ciscopartnermarketing.com/");
			}else if(Math.abs(realYInTouchPage-157)<20&&evPageX>215&&evPageX<264) {
				return openAWebLink("http://www.ciscopartnermarketing.com/");
			}else if(Math.abs(realYInTouchPage-157)<20&&evPageX>457&&evPageX<580) {
				return openAWebLink("http://www.ciscopartnermarketing.com/");
			}else if(Math.abs(realYInTouchPage-221)<20&&evPageX>215&&evPageX<319) {
				return openAWebLink("http://www.cisco.com/web/partners/sell/smb/tools_and_resources/validated_commercial_solutions.html");
			}else if(Math.abs(realYInTouchPage-264)<20&&evPageX>212&&evPageX<294) {
				return openAWebLink("http://www.cisco.com/web/CN/solutions/industry/segment_sol/enterprise/programs_for_large_enterprise/iba_design_zone.html");
			}else if(Math.abs(realYInTouchPage-311)<20&&evPageX>212&&evPageX<355) {
				return openAWebLink("http://www.cisco.com/web/go/practicebuilder");
			}else if(Math.abs(realYInTouchPage-373)<20&&evPageX>214&&evPageX<300) {
				return openAWebLink("http://www.cisco.com/web/partners/tools/steps-to-success/index.html");
			}
	    	break;
    	case 13:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>488&&evPageX<632) {
				return openAWebLink("http://cisco.ambow.net/index.jsp");
			}
	    	break;
    	case 14:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>481&&evPageX<628) {
				return openAWebLink("http://www.cisco.com/go/pec/");
			}
	    	break;
    	case 15:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>494&&evPageX<649) {
				return openAWebLink("http://cisco.ambow.net/index.jsp");
			}
	    	break;
    	case 16:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>425&&evPageX<579) {
				return openAWebLink("http://cisco.ambow.net/index.jsp");
			}
	    	break;
    	case 17:
    		if(Math.abs(realYInTouchPage-105)<20&&evPageX>425&&evPageX<579) {
				return openAWebLink("http://cisco.ambow.net/index.jsp");
			}
	    	break;
    	case 18:
    		if(Math.abs(realYInTouchPage-263)<20&&evPageX>425&&evPageX<517) {
				return openAWebLink("http://www.cisco.com/web/partners/downloads/765/other/cisco_small_business_overview.pdf");
			}
	    	break;
    	case 20:
    		if(Math.abs(realYInTouchPage-201)<20&&evPageX>222&&evPageX<259) {
				return openAWebLink("http://tools.cisco.com/WWChannels/IPA/welcome.do");
			}else if(Math.abs(realYInTouchPage-201)<20&&evPageX>594&&evPageX<625) {
				return openAWebLink("http://tools.cisco.com/WWChannels/IPA/welcome.do");
			}
	    	break;
    	case 22:
    		if(Math.abs(realYInTouchPage-151)<20&&evPageX>223&&evPageX<296) {
				return openAWebLink("http://www.cisco.com/web/CN/solutions/industry/segment_sol/smb/index.html");
			}else if(Math.abs(realYInTouchPage-372)<20&&evPageX>223&&evPageX<314) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/product_detail.html");
			}
	    	break;
    	case 24:
    		if(Math.abs(realYInTouchPage-170)<20&&evPageX>385&&evPageX<476) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/product_detail.html");
			}
	    	break;
    	case 25:
    		if(Math.abs(realYInTouchPage-251)<20&&evPageX>203&&evPageX<265) {
				return openAWebLink("http://ccuc.ambow.net/inv/100City_course.html");
			}else if(Math.abs(realYInTouchPage-289)<20&&evPageX>203&&evPageX<314) {
				return openAWebLink("http://www.ciscopartnermarketing.com/");
			}
	    	break;
    	case 27:
    		if(Math.abs(realYInTouchPage-123)<20&&evPageX>223&&evPageX<314) {
				return openAWebLink("http://service.ciscochannel.com.cn/index.jsp?soft=true");
			}else if(Math.abs(realYInTouchPage-307)<20&&evPageX>366&&evPageX<454) {
				return openAWebLink("csg-cn@cisco.com");
			}
	    	break;
    	case 28:
    		if(Math.abs(realYInTouchPage-261)<20&&evPageX>222&&evPageX<304) {
				return openAWebLink("http://pled.cisco-club.com.cn/memcp.php");
			}else if(Math.abs(realYInTouchPage-379)<20&&evPageX>202&&evPageX<324) {
				return openAWebLink("http://www.cisco.com/web/CN/ordering/ciscocapital/index.html");
			}else if(Math.abs(realYInTouchPage-500)<20&&evPageX>211&&evPageX<291) {
				return openAWebLink("http://tools.cisco.com/WWChannels/LOCATR/openBasicSearch.do");
			}else if(Math.abs(realYInTouchPage-554)<20&&evPageX>211&&evPageX<314) {
				return openAWebLink("https://www.ciscopartnermarketing.com/Orgs/Default.aspx");
			}
	    	break;
    	case 29:
    		if(Math.abs(realYInTouchPage-122)<20&&evPageX>210&&evPageX<323) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/index.html");
			}else if(Math.abs(realYInTouchPage-161)<20&&evPageX>209&&evPageX<352) {
				return openAWebLink("http://www.cisco.com/web/partners/sell/smb/tools_and_resources/validated_commercial_solutions.html");
			}else if(Math.abs(realYInTouchPage-200)<20&&evPageX>211&&evPageX<272) {
				return openAWebLink("http://www.cisco.com/web/partners/sell/enablement/quickpricingtool.html");
			}else if(Math.abs(realYInTouchPage-237)<20&&evPageX>210&&evPageX<259) {
				return openAWebLink("http://www.cisco.com/web/partners/quotebuilder/index.html");
			}else if(Math.abs(realYInTouchPage-263)<20&&evPageX>210&&evPageX<325) {
				return openAWebLink("http://www.cisco.com/web/partners/downloads/765/tools/quickreference/Cisco_ROItool.xls");
			}else if(Math.abs(realYInTouchPage-287)<20&&evPageX>214&&evPageX<360) {
				return openAWebLink("http://www.cisco.com/go/pdihelpdesk");
			}else if(Math.abs(realYInTouchPage-339)<20&&evPageX>210&&evPageX<345) {
				return openAWebLink("https://www.myciscocommunity.com/community/smallbizsupport/partnerzone/pds");
			}else if(Math.abs(realYInTouchPage-391)<20&&evPageX>444&&evPageX<548) {
				return openAWebLink("https://www.myciscocommunity.com/community/smallbizsupport/partnerzone/pds");
			}
	    	break;
    	case 30:
    		if(Math.abs(realYInTouchPage-123)<20&&evPageX>209&&evPageX<275) {
				return openAWebLink("http://www.ciscowebtools.com/smbassociationtoolkit/");
			}else if(Math.abs(realYInTouchPage-147)<20&&evPageX>209&&evPageX<343) {
				return openAWebLink("http://www.cisco.com/web/partners/sell/enablement/partnerpracticebuilder/index.html");
			}else if(Math.abs(realYInTouchPage-186)<20&&evPageX>211&&evPageX<366) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/program/certifications/select/index.html");
			}else if(Math.abs(realYInTouchPage-281)<20&&evPageX>201&&evPageX<284) {
				return openAWebLink("http://www.cisco.com/web/partners/sell/competitive/index.html");
			}
	    	break;
    	case 31:
    		if(Math.abs(realYInTouchPage-149)<20&&evPageX>210&&evPageX<273) {
				return openAWebLink("http://www.cisco-club.com.cn/");
			}else if(Math.abs(realYInTouchPage-172)<20&&evPageX>212&&evPageX<293) {
				return openAWebLink("http://pled.cisco-club.com.cn/memcp.php");
			}
	    	break;
    	case 32:
    		if(Math.abs(realYInTouchPage-213)<20&&evPageX>201&&evPageX<242) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/partner_incentive_aip.html");
			}else if(Math.abs(realYInTouchPage-268)<20&&evPageX>232&&evPageX<325) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/pr11/incentive/fast_track.html");
			}
	    	break;
    	case 33:
    		if(Math.abs(realYInTouchPage-123)<20&&evPageX>202&&evPageX<366) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/promotion/index.html");
			}else if(Math.abs(realYInTouchPage-147)<20&&evPageX>203&&evPageX<293) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/tools/tools.html");
			}else if(Math.abs(realYInTouchPage-172)<20&&evPageX>202&&evPageX<294) {
				return openAWebLink("http://www.cisco.com/cisco/web/solutions/small_business/resource_center/index.html");
			}else if(Math.abs(realYInTouchPage-196)<20&&evPageX>225&&evPageX<344) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/incentives_and_promotions/index.html");
			}else if(Math.abs(realYInTouchPage-196)<20&&evPageX>364&&evPageX<485) {
				return openAWebLink("http://www.cisco.com/web/CN/partners/smb_kr/promotion/index.html");
			}else if(Math.abs(realYInTouchPage-246)<20&&evPageX>226&&evPageX<323) {
    			return openAWebLink("http://www.xiaotong.com.cn");
    		}else if(Math.abs(realYInTouchPage-246)<20&&evPageX>363&&evPageX<485) {
    			return openAWebLink("http://www.ciscostation.com.cn");
    		}else if(Math.abs(realYInTouchPage-270)<20&&evPageX>226&&evPageX<307) {
    			return openAWebLink("http://www.imcisco.com");
    		}else if(Math.abs(realYInTouchPage-270)<20&&evPageX>368&&evPageX<459) {
    			return openAWebLink("http://www.syn-cisco.com");
    		}else if(Math.abs(realYInTouchPage-293)<20&&evPageX>226&&evPageX<330) {
    			return openAWebLink("http://www.foundertech.com");
    		}
	    	break;
    	case 34:
    		if(Math.abs(realYInTouchPage-533)<20&&evPageX>36&&evPageX<273) {
				return openAWebLink("http://www.cisco.com.cn");
			}
	    	break;
	    }
    	return false;
    }
    private boolean openAWebLink(String sUrl){
//    	Log.d("fax", "openAWebLink:"+sUrl);
    	if(sUrl.equals("")) return false;
    	Intent intent = new Intent();
    	intent.setData(Uri.parse(sUrl));
    	intent.setAction(Intent.ACTION_VIEW);
    	context.startActivity(intent); //启动浏览器
    	return true;
    }
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        if (multiTouchZoom != null) {
            if (multiTouchZoom.onTouchEvent(ev)) {
                return true;
            }

            if (multiTouchZoom.isResetLastPointAfterZoom()) {
                setLastPosition(ev);
                multiTouchZoom.setResetLastPointAfterZoom(false);
            	isMove=true;//不处理MotionEvent.ACTION_UP事件
            }
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                stopScroller();
                setLastPosition(ev);
//                if (ev.getEventTime() - lastDownEventTime < DOUBLE_TAP_TIME) {
//                    zoomModel.toggleZoomControls();
//                } else {
//                    lastDownEventTime = ev.getEventTime();
//                }
                
                isMove=false;
                
//                Log.d("fax", "getScrollX:"+ getScrollX()+ ",getScrollY:"+ getScrollY()+", getCurrentPage:"+getCurrentPage()+ ", getNowPageTop:"+pages.get(getCurrentPage()).getTop());
//                Log.d("fax", "getAspectRatio:"+pages.get(0).getAspectRatio()+"， getZoom:"+zoomModel.getZoom());
                break;
            case MotionEvent.ACTION_MOVE:
			if (Math.abs(lastX - ev.getX())<20&&Math.abs(lastY - ev.getY())<20) return true;
			isMove = true;
			scrollBy((int) (lastX - ev.getX()), (int) (lastY - ev.getY()));
                setLastPosition(ev);
                break;
            case MotionEvent.ACTION_UP:
                if(!isMove) dealMyTouch(ev);
                velocityTracker.computeCurrentVelocity(1000);
                scroller.fling(getScrollX(), getScrollY(), (int) -velocityTracker.getXVelocity(), (int) -velocityTracker.getYVelocity(), getLeftLimit(), getRightLimit(), getTopLimit(), getBottomLimit());
                velocityTracker.recycle();
                velocityTracker = null;
                
                break;
        }
        return true;
    }
    private boolean isMove=false;
    private void setLastPosition(MotionEvent ev) {
        lastX = ev.getX();
        lastY = ev.getY();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    lineByLineMoveTo(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    lineByLineMoveTo(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    verticalDpadScroll(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    verticalDpadScroll(-1);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void verticalDpadScroll(int direction) {
        scroller.startScroll(getScrollX(), getScrollY(), 0, direction * getHeight() / 2);
        invalidate();
    }

    private void lineByLineMoveTo(int direction) {
        if (direction == 1 ? getScrollX() == getRightLimit() : getScrollX() == getLeftLimit()) {
            scroller.startScroll(getScrollX(), getScrollY(), direction * (getLeftLimit() - getRightLimit()), (int) (direction * pages.get(getCurrentPage()).bounds.height() / 50));
        } else {
            scroller.startScroll(getScrollX(), getScrollY(), direction * getWidth() / 2, 0);
        }
        invalidate();
    }

    private int getTopLimit() {
        return 0;
    }

    private int getLeftLimit() {
        return 0;
    }

    private int getBottomLimit() {
        return (int) pages.get(pages.size() - 1).bounds.bottom - getHeight();
    }

    private int getRightLimit() {
        return (int) (getWidth() * zoomModel.getZoom()) - getWidth();
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(Math.min(Math.max(x, getLeftLimit()), getRightLimit()), Math.min(Math.max(y, getTopLimit()), getBottomLimit()));
        viewRect = null;
    }

    RectF getViewRect() {
        if (viewRect == null) {
            viewRect = new RectF(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
        }
        return viewRect;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Page page : pages.values()) {
            page.draw(canvas);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float scrollScaleRatio = getScrollScaleRatio();
        invalidatePageSizes();
        invalidateScroll(scrollScaleRatio);
        commitZoom();
    }

    void invalidatePageSizes() {
        if (!isInitialized) {
            return;
        }
        float heightAccum = 0;
        int width = getWidth();
        float zoom = zoomModel.getZoom();
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            float pageHeight = page.getPageHeight(width, zoom);
            page.setBounds(new RectF(0, heightAccum, width * zoom, heightAccum + pageHeight));
            heightAccum += pageHeight;
        }
    }

    private void invalidateScroll(float ratio) {
        if (!isInitialized) {
            return;
        }
        stopScroller();
        final Page page = pages.get(0);
        if (page == null || page.bounds == null) {
            return;
        }
        scrollTo((int) (getScrollX() * ratio), (int) (getScrollY() * ratio));
    }

    private float getScrollScaleRatio() {
        final Page page = pages.get(0);
        if (page == null || page.bounds == null) {
            return 0;
        }
        final float v = zoomModel.getZoom();
        return getWidth() * v / page.bounds.width();
    }

    private void stopScroller() {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
    }

}
