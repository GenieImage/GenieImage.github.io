package com.genie.jinn;

import android.annotation.TargetApi;
import android.content.ContentUris;
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
import android.provider.DocumentsContract;
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
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedTransferQueue;

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

    private final int DEFAULT_SIZE = 4;    // number of images in slider

    private PicAdapter imgAdapt;    // adapter for gallery view

    private Bitmap pic;
    // LogCat tag
    private static final String TAG = MainActivity.class.getSimpleName();

    //set the width and height we want to use as maximum display
    private int targetWidth = 600;
    private int targetHeight = 400;

    private String filePath;

    long totalSize = 0;
    private String encodedString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get the large image view
        picView = (ImageView) findViewById(R.id.picture);

        //get the gallery view
        picGallery = (Gallery) findViewById(R.id.gallery);

        //create a new adapter
        imgAdapt = new PicAdapter(this);

        //set the gallery adapter
        picGallery.setAdapter(imgAdapt);

        //set long click listener for each gallery thumbnail item
        picGallery.setOnItemLongClickListener(new OnItemLongClickListener() {
            //handle long clicks
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                //take user to choose an image
                //update the currently selected position so that we assign the imported bitmap to correct item
                currentPic = position;

                //take the user to their chosen image selection app (gallery or file manager)
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                // Filter to show only images, using the image MIME data type.
                // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
                // To search for all documents available via installed storage providers,
                // it would be "*/*".
                pickIntent.setType("image/*");

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                pickIntent.addCategory(Intent.CATEGORY_OPENABLE);

                //we will handle the returned data in onActivityResult
                startActivityForResult(Intent.createChooser(pickIntent, "Select Picture"), PICKER);

                return true;
            }
        });

        //set the click listener for each item in the thumbnail gallery
        picGallery.setOnItemClickListener(new OnItemClickListener() {
            //handle clicks
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                //set the larger image view to display the chosen bitmap calling method of adapter class
                picView.setImageBitmap(imgAdapt.getPic(position));
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

                // The query, since it only applies to a single document, will only return
                // one row. There's no need to filter, sort, or select fields, since we want
                // all fields for one document.
                Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);

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
                String imgPath = uri.getPath();

                BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
                bmpOptions.inJustDecodeBounds = true;

                pic = Bitmap.createScaledBitmap(pic, targetWidth, targetHeight, false);

                //pass bitmap to ImageAdapter to add to array
                imgAdapt.addPic(pic);
                //redraw the gallery thumbnails to reflect the new addition
                picGallery.setAdapter(imgAdapt);

                //display the newly selected image at larger size
                picView.setImageBitmap(pic);
                //scale options
                picView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                // attempt to create local copy of image from uri
                File newImageFile = new File(uri.getPath());
                File path = getFilesDir();
                if (isExternalStorageWritable()) {
                    path = new File(getExternalFilesDir(
                            Environment.DIRECTORY_PICTURES), "genieTemp");
                    if (!path.mkdirs() && !path.exists()) {
                        Log.e("TEMP DIR", "Directory issues");
                    }
            }
                String dir = path.toString();
                OutputStream fOut = null;
                filePath = path.toString();
                File file = new File(path, "image"+".jpg"); // the File to save to
                try {
                    fOut = new FileOutputStream(file);

                    // saving the Bitmap to a file compressed as a JPEG with 50% compression rate
                    pic.compress(Bitmap.CompressFormat.JPEG, 50, fOut);

                    fOut.flush();
                    fOut.close();

                    MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());

                } catch (FileNotFoundException e) {
                    Toast.makeText(this, getString(R.string.TempFileError),
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                } catch (IOException e) {
                    Toast.makeText(this, getString(R.string.TempFileIOError),
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }




                //declare the bitmap
//                Bitmap pic = null;
//
//                //declare the path string
//                String imgPath = "";
//
//                //retrieve the string using media data
//                String[] medData = { MediaStore.Images.Media.DATA };
//
//                // where id is equal to
//                String sel = MediaStore.Images.Media._ID + "=?";
//
//                // Will return "image:x*"
//                //String wholeID = DocumentsContract.getDocumentId(selectedImage);
//
//                // Split at colon, use second item in the array
//                //String id = wholeID.split(":")[1];
//
//                String document_id = "";
//
//                //query the data
//                Cursor picCursor = null;
//                try {
//                    picCursor = getContentResolver().query(selectedImage, null, null, null, null);
//                    picCursor.moveToFirst();
//                    document_id = picCursor.getString(0);
//                    document_id = document_id.substring(document_id.lastIndexOf(":")+1);
//                    picCursor.close();
//                } catch(Exception e) {
//                    Toast.makeText(this, "Something went wrong. Please try a new file.", Toast.LENGTH_LONG)
//                            .show();
//                }
//
//                if(picCursor != null)
//                {
//                    picCursor = getContentResolver().query(
//                            android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI,
//                            null, MediaStore.Images.Media._ID + " = ? ", new String[]{document_id}, null);
//                    picCursor.moveToFirst();
//                    //get the path string
//                    int index = picCursor.getColumnIndex(medData[0]);
////                    picCursor.moveToFirst();
//                    imgPath = picCursor.getString(picCursor.getColumnIndex(MediaStore.Images.Media.DATA));
//                    picCursor.close();
//                }
//                else
//                    imgPath = selectedImage.getPath();
////                imgPath = getPath(this, selectedImage);
//
//                //if we have a new URI attempt to decode the image bitmap
//                if(selectedImage!=null) {
//                    //create bitmap options to calculate and use sample size
//                    BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
//
//                    //first decode image dimensions only - not the image bitmap itself
////                    bmpOptions.inJustDecodeBounds = true;
//                    BitmapFactory.decodeFile(imgPath, bmpOptions);
//
//                    //image width and height before sampling
//                    int currHeight = bmpOptions.outHeight;
//                    int currWidth = bmpOptions.outWidth;
//
//                    //variable to store new sample size
//                    int sampleSize = 1;
//
//                    //calculate the sample size if the existing size is larger than target size
//                    if (currHeight>targetHeight || currWidth>targetWidth)
//                    {
//                        //use either width or height
//                        if (currWidth>currHeight)
//                            sampleSize = Math.round((float)currHeight/(float)targetHeight);
//                        else
//                            sampleSize = Math.round((float)currWidth/(float)targetWidth);
//                    }
//
//                    //use the new sample size
//                    bmpOptions.inSampleSize = sampleSize;
//
//                    //now decode the bitmap using sample options
//                    bmpOptions.inJustDecodeBounds = false;
//
//                    //get the file as a bitmap
//                    pic = BitmapFactory.decodeFile(imgPath, bmpOptions);
//
//                    //pass bitmap to ImageAdapter to add to array
//                    imgAdapt.addPic(pic);
//                    //redraw the gallery thumbnails to reflect the new addition
//                    picGallery.setAdapter(imgAdapt);
//
//                    //display the newly selected image at larger size
//                    picView.setImageBitmap(pic);
//                    //scale options
//                    picView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//                }
            }
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

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
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
            imageView.setLayoutParams(new Gallery.LayoutParams(300, 200));
            //scale type within view area
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            //set default gallery item background
            imageView.setBackgroundResource(defaultItemBackground);
            //return the view
            return imageView;
        }

        //helper method to add a bitmap to the gallery when the user chooses one
        public void addPic(Bitmap newPic)
        {
            //set at currently selected index
            imageBitmaps[currentPic] = newPic;
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

    public void encodeImagetoString() {
        new AsyncTask<Void, Void, String>() {

            protected void onPreExecute() {

            };

            @Override
            protected String doInBackground(Void... params) {
                BitmapFactory.Options options = null;
                options = new BitmapFactory.Options();
                options.inSampleSize = 3;
//                bitmap = BitmapFactory.decodeFile(imgPath,
//                        options);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                // Must compress the Image to reduce image size to make upload easy
                pic.compress(Bitmap.CompressFormat.JPEG, 99, stream);
                byte[] byte_arr = stream.toByteArray();
                // Encode Image to String
                encodedString = Base64.encodeToString(byte_arr, 0);
                return "";
            }

            @Override
            protected void onPostExecute(String msg) {
                Log.d(TAG, "CALLING UPLOAD");
//                prgDialog.setMessage("Calling Upload");
                // Put converted Image string into Async Http Post param
                LinkedTransferQueue<String> params;
                params.put("image", encodedString);
                // Trigger Image upload
                triggerImageUpload();
            }
        }.execute(null, null, null);
    }

}
