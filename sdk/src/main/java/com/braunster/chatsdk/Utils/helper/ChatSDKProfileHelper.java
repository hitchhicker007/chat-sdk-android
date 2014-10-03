package com.braunster.chatsdk.Utils.helper;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.braunster.chatsdk.R;
import com.braunster.chatsdk.Utils.ImageUtils;
import com.braunster.chatsdk.Utils.volley.VolleyUtils;
import com.braunster.chatsdk.dao.BMetadata;
import com.braunster.chatsdk.dao.BUser;
import com.braunster.chatsdk.interfaces.CompletionListenerWithData;
import com.braunster.chatsdk.network.BDefines;
import com.braunster.chatsdk.network.BFacebookManager;
import com.braunster.chatsdk.network.BNetworkManager;
import com.braunster.chatsdk.network.TwitterManager;
import com.braunster.chatsdk.object.BError;
import com.braunster.chatsdk.object.Cropper;
import com.braunster.chatsdk.parse.ParseUtils;
import com.facebook.model.GraphUser;
import com.parse.ParseException;
import com.soundcloud.android.crop.Crop;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Created by braunster on 22/09/14.
 */
public class ChatSDKProfileHelper {

    public static final int ERROR = 1991, NOT_HANDLED = 1992, HANDELD = 1993;

    private static final String TAG = ChatSDKProfileHelper.class.getSimpleName();
    private static final boolean DEBUG = true;

    protected Cropper crop;

    private Fragment fragment = null;

    public static final int PROFILE_PIC = 100;

    public CircleImageView profileCircleImageView;
    public ProgressBar progressBar;

    private Activity activity;

    private ChatSDKUiHelper uiHelper;

    private View mainView;

    public ChatSDKProfileHelper(Activity activity, CircleImageView profileCircleImageView, ProgressBar progressBar, ChatSDKUiHelper uiHelper, View mainView) {
        this.profileCircleImageView = profileCircleImageView;
        this.progressBar = progressBar;
        this.activity = activity;
        this.uiHelper = uiHelper;
        this.mainView = mainView;
    }

    /* UI*/
    public void loadProfilePic(int loginType){
        profileCircleImageView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        switch (loginType)
        {
            case BDefines.BAccountType.Facebook:
                getProfileFromFacebook();
                break;

            case BDefines.BAccountType.Password:
                setProfilePicFromURL(BNetworkManager.sharedManager().getNetworkAdapter().currentUser().metaStringForKey(BDefines.Keys.BPictureURL));
                break;

            case BDefines.BAccountType.Anonymous:
                setInitialsProfilePic(BDefines.InitialsForAnonymous);

            case BDefines.BAccountType.Twitter:
                getProfileFromTwitter();
                break;
        }
    }

    private class PostProfilePic implements Runnable{

        private Bitmap bitmap;

        private PostProfilePic(Bitmap bitmap){
            this.bitmap = bitmap;
        }

        @Override
        public void run() {
            int size = mainView.findViewById(R.id.frame_profile_image_container).getMeasuredHeight();
            profileCircleImageView.setImageBitmap(scaleImage(bitmap, size));
            progressBar.setVisibility(View.GONE);
            profileCircleImageView.setVisibility(View.VISIBLE);
        }
    }

    private PostProfilePic postProfilePic;

    public void setProfilePic(final Bitmap bitmap){
        if (DEBUG) Log.v(TAG, "setProfilePic, Width: " + bitmap.getWidth() + ", Height: " + bitmap.getHeight());
        // load image into imageview
        final int size = mainView.findViewById(R.id.frame_profile_image_container).getMeasuredHeight();

        if (loadFromUrl != null)
            loadFromUrl.setKilled(true);

        if (postProfilePic != null)
            profileCircleImageView.removeCallbacks(postProfilePic);

        // If the size of the container is 0 we will wait for the view to do onLayout and only then measure it.
        if (size == 0)
        {
            if (DEBUG) Log.d(TAG, "setProfilePic, Size == 0");
            postProfilePic = new PostProfilePic(bitmap);

            profileCircleImageView.post(postProfilePic);
        } else
        {
            if (DEBUG) Log.d(TAG, "setProfilePic, Has Size");
            profileCircleImageView.setImageBitmap(scaleImage(bitmap, size));
            progressBar.setVisibility(View.GONE);
            profileCircleImageView.setVisibility(View.VISIBLE);
        }
    }

    public class LoadFromUrl implements ImageLoader.ImageListener{
        private boolean killed = false;

        public void setKilled(boolean killed) {
            this.killed = killed;
        }

        @Override
        public void onResponse(final ImageLoader.ImageContainer response, boolean isImmediate) {

            if (killed)
                return;

            if (response.getBitmap() != null) {
                if (DEBUG) Log.v(TAG, "onResponse, Profile pic loaded from url.");
                setProfilePic(response.getBitmap());
            }
        }


        @Override
        public void onErrorResponse(VolleyError error) {
            if (DEBUG) Log.e(TAG, "Image Load Error: " + error.getMessage());
            setInitialsProfilePic();
        }
    };

    private LoadFromUrl loadFromUrl;

    public void setProfilePicFromURL(String url){
        // Set default.
        if (url == null)
        {
            setInitialsProfilePic();
            return;
        }

        if (loadFromUrl != null)
            loadFromUrl.setKilled(true);

        loadFromUrl = new LoadFromUrl();

        VolleyUtils.getImageLoader().get(url, loadFromUrl);
    }

    public void saveProfilePicToParse(String path, boolean setAsPic) {
        if (DEBUG) Log.v(TAG, "saveProfilePicToParse, Path: " + path);

        //  Loading the bitmap
        if (setAsPic)
        {
            if (DEBUG) Log.d(TAG, "SetAsPic");
            Bitmap b = ImageUtils.loadBitmapFromFile(path);

            if (b == null)
            {
                b = ImageUtils.loadBitmapFromFile(activity.getCacheDir().getPath() + path);
                if (b == null)
                {
                    uiHelper.showAlertToast("Unable to save file...");
                    if (DEBUG) Log.e(TAG, "Cant save image to parse file path is invalid: " + activity.getCacheDir().getPath() + path);
                    return;
                }
            }
            setProfilePic(b);
        }

        saveProfilePicToParse(path);
    }

    public void saveProfilePicToParse(String path){
        ParseUtils.saveImageFileToParseWithThumbnail(path, BDefines.ImageProperties.PROFILE_PIC_THUMBNAIL_SIZE, new ParseUtils.MultiSaveCompletedListener() {
            @Override
            public void onSaved(ParseException exception, String... data) {
                if (exception != null) {
                    if (DEBUG)
                        Log.e(TAG, "Parse Exception while saving profile pic --- " + exception.getMessage());
                    return;
                }

                // Saving the image to parse.
                final BUser currentUser = BNetworkManager.sharedManager().getNetworkAdapter().currentUser();

                currentUser.setMetadataString(BDefines.Keys.BPictureURL, data[0]);
                currentUser.setMetadataString(BDefines.Keys.BPictureURLThumbnail, data[1]);

                currentUser.fetchOrCreateMetadataForKey(BDefines.Keys.BPictureURL, BMetadata.Type.STRING);
                currentUser.fetchOrCreateMetadataForKey(BDefines.Keys.BPictureURLThumbnail, BMetadata.Type.STRING);

                BNetworkManager.sharedManager().getNetworkAdapter().pushUserWithCallback(null);
            }
        });
    }

    public void getProfileFromFacebook(){
        // Use facebook profile picture only if has no other picture saved.
        String imageUrl = BNetworkManager.sharedManager().getNetworkAdapter().currentUser().getMetaPictureUrl();

        if (imageUrl != null)
            setProfilePicFromURL(imageUrl);
        else
        {
            // If there isnt any picture url saved we will save the fb profile pic to parse and save the url to the user.
            BFacebookManager.getUserDetails(new CompletionListenerWithData<GraphUser>() {
                @Override
                public void onDone(GraphUser graphUser) {
                    Log.d(TAG, "Name: " + graphUser.getName());

                    VolleyUtils.getImageLoader().get(BFacebookManager.getPicUrl(graphUser.getId()),
                            new ImageLoader.ImageListener() {
                                @Override
                                public void onResponse(final ImageLoader.ImageContainer response, boolean isImmediate) {
                                    if (response.getBitmap() != null) {
                                        setProfilePic(response.getBitmap());

                                        createTempFileAndSave(response.getBitmap());
                                    }
                                }

                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    setInitialsProfilePic();
                                    uiHelper.showAlertToast("Unable to load user profile pic.");
                                }
                            });
                }

                ;

                @Override
                public void onDoneWithError(BError error) {
                    setInitialsProfilePic();
                    uiHelper.showAlertToast("Unable to fetch user details from fb" + (error != null ? error.code == BError.Code.EXCEPTION ? error.message : "" : ""));
                }
            });
        }
    }

    public void getProfileFromTwitter(){
        // Use facebook profile picture only if has no other picture saved.
        String savedUrl = BNetworkManager.sharedManager().getNetworkAdapter().currentUser().getMetaPictureUrl();

        if (StringUtils.isNotEmpty(savedUrl))
            setProfilePicFromURL(savedUrl);
        else {
            String imageUrl = TwitterManager.profileImageUrl;
            if (DEBUG) Log.d(TAG, "Image URL: " + imageUrl);
            if (StringUtils.isNotEmpty(imageUrl))
            {
                // The default image suppied by twitter is 48px on 48px image so we want a bigget one.
                imageUrl = imageUrl.replace("_normal", "");
                VolleyUtils.getImageLoader().get(imageUrl,
                        new ImageLoader.ImageListener() {
                            @Override
                            public void onResponse(final ImageLoader.ImageContainer response, boolean isImmediate) {
                                if (response.getBitmap() != null) {

                                    setProfilePic(response.getBitmap());

                                    createTempFileAndSave(response.getBitmap());
                                }
                            }

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                setInitialsProfilePic();
                                uiHelper.showAlertToast("Unable to load user profile pic.");
                            }
                        }
                );
            }
        }
    }

    public void setInitialsProfilePic(){

        String initials = "";

        String name = BNetworkManager.sharedManager().getNetworkAdapter().currentUser().getMetaName();
        if (DEBUG) Log.v(TAG, "setInitialsProfilePic, Name: " + name);

        if (StringUtils.isEmpty(name))
            initials = BDefines.InitialsForAnonymous;
        else
        {
            String[] splited = name.split("\\s+");
            if (splited.length == 1)
                initials = String.valueOf(name.toUpperCase().charAt(0));
            else if (splited.length >= 2)
                initials = String.valueOf(splited[0].toUpperCase().charAt(0)) + String.valueOf(splited[1].toUpperCase().charAt(0));
            else initials = BDefines.InitialsForAnonymous;
        }

        setInitialsProfilePic(initials);
    }

    public void setInitialsProfilePic(final String initials) {
        if (DEBUG) Log.v(TAG, "setInitialsProfilePic, Initials: " + initials);
        Bitmap bitmap = ImageUtils.getInitialsBitmap(Color.GRAY, Color.BLACK, initials);
        setProfilePic(bitmap);
        createTempFileAndSave(bitmap);
    }

    public boolean createTempFileAndSave(Bitmap bitmap){
        if (DEBUG) Log.v(TAG, "createTempFileAndSave");

        // Saving the image to tmp file.
        try {
            File tmp = File.createTempFile("Pic", ".jpg", activity.getCacheDir());
            ImageUtils.saveBitmapToFile(tmp, bitmap);
            if (DEBUG) Log.i(TAG, "Temp file path: " + tmp.getPath());
            saveProfilePicToParse(tmp.getPath(), false);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Bitmap scaleImage(Bitmap bitmap, int boundBoxInDp){
        if (boundBoxInDp == 0)
            return null;

        // Get current dimensions
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Determine how much to scale: the dimension requiring less scaling is
        // closer to the its side. This way the image always stays inside your
        // bounding box AND either x/y axis touches it.
        float xScale = ((float) boundBoxInDp) / width;
        float yScale = ((float) boundBoxInDp) / height;
        float scale = (xScale <= yScale) ? xScale : yScale;

        // Create a matrix for the scaling and add the scaling data
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        // Create a new bitmap and convert it to a format understood by the ImageView
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

        return bitmap;
    }

    public int handleResult(int requestCode, int resultCode, Intent data){
        if (data == null)
        {
            if (DEBUG) Log.e(TAG, "onActivityResult, Intent is null");
            return NOT_HANDLED;
        }

        if (DEBUG) Log.v(TAG, "onActivityResult");

        if (requestCode == PROFILE_PIC)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                if (DEBUG) Log.d(TAG, "Result OK");
                Uri uri = data.getData();

                Uri outputUri = Uri.fromFile(new File(this.activity.getCacheDir(), "cropped.jpg"));
                crop = new Cropper(uri);

                Intent cropIntent = crop.getIntent(this.activity, outputUri);
                int request =Crop.REQUEST_CROP + PROFILE_PIC;

                if (fragment==null)
                    activity.startActivityForResult(cropIntent, request);
                else ((FragmentActivity) activity).startActivityFromFragment(fragment, cropIntent, request);

                return HANDELD;
            }
        }
        else  if (requestCode == Crop.REQUEST_CROP + PROFILE_PIC) {
            if (resultCode == Crop.RESULT_ERROR)
            {
                if (DEBUG) Log.e(TAG, "Result Error");
                return ERROR;
            }

            try
            {
                File image;
                Uri uri = Crop.getOutput(data);

                if (DEBUG) Log.d(TAG, "Fetch image URI: " + uri.toString());
                image = new File(this.activity.getCacheDir(), "cropped.jpg");
                saveProfilePicToParse(image.getPath(), true);

                return HANDELD;
            }
            catch (NullPointerException e){
                if (DEBUG) Log.e(TAG, "Null pointer when getting file.");
                uiHelper.showAlertToast("Unable to fetch image");
                return ERROR;
            }
        }

        return NOT_HANDLED;
    }

    public static View.OnClickListener getProfilePicClickListener(final Activity activity){
        return ChatSDKIntentClickListener.getPickImageClickListener(activity, PROFILE_PIC);
    }

    public static View.OnClickListener getProfilePicClickListener(final Activity activity, final Fragment fragment){
        return ChatSDKIntentClickListener.getPickImageClickListener((FragmentActivity) activity, fragment, PROFILE_PIC);
    }

    public static View.OnClickListener getPickImageClickListener(final FragmentActivity activity,final Fragment fragment, final int requestCode){
        return ChatSDKIntentClickListener.getPickImageClickListener(activity, fragment, requestCode);
    }

    public static View.OnClickListener getPickImageClickListener(final FragmentActivity activity,final DialogFragment fragment, final int requestCode){
        return ChatSDKIntentClickListener.getPickImageClickListener(activity, fragment, requestCode);
    }

    /** If set the helper will use this fragment when calling startActivityForResult*/
    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
    }
}