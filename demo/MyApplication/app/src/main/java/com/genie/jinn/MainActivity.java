package com.genie.jinn;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;

import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends ActionBarActivity {

    /* for identifying the image chooser Intent when
       the user's chosen image is returned to the app */
    private final int PICKER = 1;   // variable for selection intent

    /* keep track of which thumbnail they are selecting an
        image for; used when user is taken outside app to choose image */
    private int currentPic = 0; // variable to store the currently selected image

    private Gallery picGallery; // gallery object

    private ImageView picView;  // image view for larger display

    private final int DEFAULT_SIZE = 7;    // number of images in slider

    private PicAdapter imgAdapt;    // adapter for gallery view

    private String[] imgPaths;

    private Bitmap pic;
    // LogCat tag
    private static final String TAG = MainActivity.class.getSimpleName();

    ProgressDialog prgDialog;
    RequestParams params = new RequestParams();

    Button upload;

    //set the width and height we want to use as maximum display
    private int targetWidth = 640;
    private int targetHeight = 480;

    private String filePath;

    long totalSize = 0;
    private String encodedString;

    private Bitmap bitmap;
    private String curImgPath;

    private static int RESULT_LOAD_IMG = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prgDialog = new ProgressDialog(this);
        // Set Cancelable as False
        prgDialog.setCancelable(false);
        //get the large image view

        picView = (ImageView) findViewById(R.id.picture);

        //get the gallery view
        picGallery = (Gallery) findViewById(R.id.gallery);

        upload = (Button) findViewById(R.id.upload);

        //create a new adapter
        imgAdapt = new PicAdapter(this);

        //set the gallery adapter
        picGallery.setAdapter(imgAdapt);

        imgPaths = new String[DEFAULT_SIZE];

        //set long click listener for each gallery thumbnail item
        picGallery.setOnItemLongClickListener(new OnItemLongClickListener() {
            //handle long clicks
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                //take user to choose an image
                //update the currently selected position so that we assign the imported bitmap to correct item
                currentPic = position;

                //take the user to their chosen image selection app (gallery or file manager)
                // Create intent to Open Image applications like Gallery, Google Photos
                Intent pickIntent = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                //we will handle the returned data in onActivityResult
                // Start the Intent
                startActivityForResult(pickIntent, RESULT_LOAD_IMG);

                return true;
            }
        });

        //set the click listener for each item in the thumbnail gallery
        picGallery.setOnItemClickListener(new OnItemClickListener() {
            //handle clicks
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                //set the larger image view to display the chosen bitmap calling method of adapter class
                bitmap = imgAdapt.getPic(position);
                picView.setImageBitmap(bitmap);
                curImgPath = imgPaths[position];
            }
        });

        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //superclass method
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            //check if we are returning from picture selection
            if (requestCode == PICKER) {
                //import the image

                //the returned picture URI
                Uri uri = data.getData();
                Log.i("URI", "Uri: " + uri.toString());

                // extra to handle imgPath obtaining issue
                String imgPath;
                String[] filePathColumn = { MediaStore.Images.Media.DATA };

                // Get the cursor
                Cursor cursor = getContentResolver().query(uri,
                        filePathColumn, null, null, null);
                // Move to first row
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);


                // The query, since it only applies to a single document, will only return
                // one row. There's no need to filter, sort, or select fields, since we want
                // all fields for one document.
                cursor = getContentResolver().query(uri, null, null, null, null, null);

                try {
                    // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                    // "if there's anything to look at, look at it" conditionals.
                    if (cursor != null && cursor.moveToFirst()) {

                        // Note it's called "Display Name".  This is
                        // provider-specific, and might not necessarily be the file name.
                        String displayName = cursor.getString(
                                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        Log.i("DISPLAY NAME", "Display Name: " + displayName);

                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        // If the size is unknown, the value stored is null.  But since an
                        // int can't be null in Java, the behavior is implementation-specific,
                        // which is just a fancy term for "unpredictable".  So as
                        // a rule, check if it's null before assigning to an int.  This will
                        // happen often:  The storage API allows for remote files, whose
                        // size might not be locally known.
                        String size = null;
                        if (!cursor.isNull(sizeIndex)) {
                            // Technically the column stores an int, but cursor.getString()
                            // will do the conversion automatically.
                            size = cursor.getString(sizeIndex);
                        } else {
                            size = "Unknown";
                        }
                        Log.i("SIZE", "Size: " + size);
                    }
                } finally {
                    cursor.close();
                }

                pic = null;
                try {
                    pic = getBitmapFromUri(uri);
                } catch (IOException e) {
                    Toast.makeText(this, "Something went wrong. Please try a new file.",
                            LENGTH_LONG).show();
                    e.printStackTrace();
                }

                BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
                bmpOptions.inJustDecodeBounds = true;

                pic = Bitmap.createScaledBitmap(pic, targetWidth, targetHeight, false);

                //pass bitmap to ImageAdapter to add to array
                imgAdapt.addPic(pic, imgPath);
                //redraw the gallery thumbnails to reflect the new addition
                picGallery.setAdapter(imgAdapt);

                //display the newly selected image at larger size
                picView.setImageBitmap(pic);
                //scale options
                picView.setScaleType(ImageView.ScaleType.FIT_CENTER);

//                // attempt to create local copy of image from uri
//                File newImageFile = new File(uri.getPath());
//                File path = getFilesDir();
//                if (isExternalStorageWritable()) {
//                    path = new File(getExternalFilesDir(
//                            Environment.DIRECTORY_PICTURES), "genieTemp");
//                    if (!path.mkdirs() && !path.exists()) {
//                        Log.e("TEMP DIR", "Directory issues");
//                    }
//                }
//                String dir = path.toString();
//                OutputStream fOut = null;
//                filePath = path.toString();
//                File file = new File(path, "image"+".jpg"); // the File to save to
//                try {
//                    fOut = new FileOutputStream(file);
//
//                    // saving the Bitmap to a file compressed as a JPEG with 50% compression rate
//                    pic.compress(Bitmap.CompressFormat.JPEG, 50, fOut);
//
//                    fOut.flush();
//                    fOut.close();
//
//                    MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
//
//                } catch (FileNotFoundException e) {
//                    Toast.makeText(this, getString(R.string.TempFileError),
//                            Toast.LENGTH_LONG).show();
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    Toast.makeText(this, getString(R.string.TempFileIOError),
//                            Toast.LENGTH_LONG).show();
//                    e.printStackTrace();
//                }
            }
        }
    }

    // When Upload button is clicked
    public void uploadImage() {
        // When Image is selected from Gallery
        if (curImgPath != null && !curImgPath.isEmpty()) {
            prgDialog.setMessage("Converting Image to Binary Data");
            prgDialog.show();
            // Convert image to String using Base64
            encodeImagetoString();
            // When Image is not selected from Gallery
        } else {
            Toast.makeText(
                    getApplicationContext(),
                    "You must select image from gallery before you try to upload",
                    Toast.LENGTH_LONG).show();
        }
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private String getRealPathFromURI(Uri contentURI) {
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            return contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(idx);
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    public class PicAdapter extends BaseAdapter {

        // use the default gallery background image
        int defaultItemBackground;

        // gallery context
        private Context galleryContext;

        // array to store bitmaps to display
        private Bitmap[] imageBitmaps;

        // placeholder bitmap for empty spaces in gallery
        Bitmap placeholder;

        public PicAdapter(Context c) {

            //instantiate context
            galleryContext = c;

            //create bitmap array
            imageBitmaps  = new Bitmap[DEFAULT_SIZE];

            //decode the placeholder image
            placeholder = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

            //more processing
            //set placeholder as all thumbnail images in the gallery initially
            for(int i=0; i<imageBitmaps.length; i++)
                imageBitmaps[i]=placeholder;

            //get the styling attributes - use default Andorid system resources
            TypedArray styleAttrs = galleryContext.obtainStyledAttributes(R.styleable.PicGallery);

            //get the background resource
            defaultItemBackground = styleAttrs.getResourceId(
                    R.styleable.PicGallery_android_galleryItemBackground, 0);

            //recycle attributes
            styleAttrs.recycle();

        }

        @Override
        public int getCount() {
            return imageBitmaps.length;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        //get view specifies layout and display options for each thumbnail in the gallery
        public View getView(int position, View convertView, ViewGroup parent) {

            //create the view
            ImageView imageView = new ImageView(galleryContext);
            //specify the bitmap at this position in the array
            imageView.setImageBitmap(imageBitmaps[position]);
            //set layout options
            imageView.setLayoutParams(new Gallery.LayoutParams(360, 240));
            //scale type within view area
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            //set default gallery item background
            imageView.setBackgroundResource(defaultItemBackground);
            //return the view
            return imageView;
        }

        //helper method to add a bitmap to the gallery when the user chooses one
        public void addPic(Bitmap newPic, String imgPath)
        {
            //set at currently selected index
            imageBitmaps[currentPic] = newPic;
            imgPaths[currentPic] = imgPath;
        }

        //return bitmap at specified position for larger display
        public Bitmap getPic(int posn)
        {
            //return bitmap at posn index
            return imageBitmaps[posn];
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // AsyncTask - To convert Image to String
    public void encodeImagetoString() {
        new AsyncTask<Void, Void, String>() {

            protected void onPreExecute() {

            };

            @Override
            protected String doInBackground(Void... params) {
                BitmapFactory.Options options = null;
                options = new BitmapFactory.Options();
                options.inSampleSize = 3;
                bitmap = BitmapFactory.decodeFile(curImgPath,
                        options);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                // Must compress the Image to reduce image size to make upload easy
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                byte[] byte_arr = stream.toByteArray();
                // Encode Image to String
                encodedString = Base64.encodeToString(byte_arr, 0);
                return "";
            }

            @Override
            protected void onPostExecute(String msg) {
                prgDialog.setMessage("Calling Upload");
                // Put converted Image string into Async Http Post param
                params.put("image", encodedString);
                // Trigger Image upload
                triggerImageUpload();
            }
        }.execute(null, null, null);
    }

    public void triggerImageUpload() {
        makeHTTPCall();
    }

    // http://192.168.2.4:9000/imgupload/upload_image.php
    // http://192.168.2.4:9999/ImageUploadWebApp/uploadimg.jsp
    // Make Http call to upload Image to Php server
    public void makeHTTPCall() {
        prgDialog.setMessage("Invoking Php");
        AsyncHttpClient client = new AsyncHttpClient();
        // Don't forget to change the IP address to your LAN address. Port no as well.
        client.post("http://192.168.0.2:80/~Debosmit/sensei/upload_image.php",
                params, new AsyncHttpResponseHandler() {
                    // When the response returned by REST has Http
                    // response code '200'
                    @Override
                    public void onSuccess(String response) {
                        // Hide Progress Dialog
                        prgDialog.hide();
                        Toast.makeText(getApplicationContext(), response,
                                Toast.LENGTH_LONG).show();
                    }

                    // When the response returned by REST has Http
                    // response code other than '200' such as '404',
                    // '500' or '403' etc
                    @Override
                    public void onFailure(int statusCode, Throwable error,
                                          String content) {
                        // Hide Progress Dialog
                        prgDialog.hide();
                        // When Http response code is '404'
                        if (statusCode == 404) {
                            Toast.makeText(getApplicationContext(),
                                    "Requested resource not found",
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code is '500'
                        else if (statusCode == 500) {
                            Toast.makeText(getApplicationContext(),
                                    "Something went wrong at server end",
                                    Toast.LENGTH_LONG).show();
                        }
                        // When Http response code other than 404, 500
                        else {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Error Occured \n Most Common Error: \n1. Device not connected to Internet\n2. Web App is not deployed in App server\n3. App server is not running\n HTTP Status code : "
                                            + statusCode, Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        // Dismiss the progress bar when application is closed
        if (prgDialog != null) {
            prgDialog.dismiss();
        }
    }

}
