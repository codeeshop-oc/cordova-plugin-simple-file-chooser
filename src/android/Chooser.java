package in.foobars.cordova;

import java.io.OutputStream; 
import org.apache.cordova.CordovaPlugin;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import org.json.JSONObject;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import android.app.Activity;
import android.content.Intent;

public class Chooser extends CordovaPlugin {
    private static final String ACTION_OPEN = "getFiles";
    private static final String ACTION_OPEN_FOLDER = "getFolder";
    private static final int PICK_FILE_REQUEST = 1;
    private static final int PICK_FOLDER_REQUEST = 2;
    private static final String TAG = "Chooser";
    private static String FILE_CONTENT_MAIN = "";

    public static String getDisplayName(ContentResolver contentResolver, Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = contentResolver.query(uri, projection, null, null, null);

        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    return metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }

        return null;
    }

    private CallbackContext callback;

    public void chooseFile(CallbackContext callbackContext, String accept) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, accept.split(","));
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        Intent chooser = Intent.createChooser(intent, "Select File");
        cordova.startActivityForResult(this, chooser, Chooser.PICK_FILE_REQUEST);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    public void chooseFolder(CallbackContext callbackContext, String fileName, String fileContent) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/octet-stream");
        // Specify the initial file name (optional)
        intent.putExtra(Intent.EXTRA_TITLE, fileName);;
        Chooser.FILE_CONTENT_MAIN = fileContent;
        cordova.startActivityForResult(this, intent, Chooser.PICK_FOLDER_REQUEST);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);        
    }
 
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            if (action.equals(Chooser.ACTION_OPEN)) {
                this.chooseFile(callbackContext, args.getString(0));
                return true;
            } else if (action.equals(Chooser.ACTION_OPEN_FOLDER)) {
                this.chooseFolder(callbackContext, args.getString(0), args.getString(1));
                return true;
            }
        } catch (JSONException err) {
            this.callback.error("Execute failed: " + err.toString());
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == Chooser.PICK_FILE_REQUEST && this.callback != null) {
                if (resultCode == Activity.RESULT_OK) {
                    JSONArray files = new JSONArray();
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            files.put(processFileUri(data.getClipData().getItemAt(i).getUri()));
                        }
                        this.callback.success(files.toString());
                    } else if (data.getData() != null) {
                        files.put(processFileUri(data.getData()));
                        this.callback.success(files.toString());
                    } else {
                        this.callback.error("File URI was null.");
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    this.callback.error("RESULT_CANCELED");
                } else {
                    this.callback.error(resultCode);
                }
            } else if (requestCode == Chooser.PICK_FOLDER_REQUEST && this.callback != null) {
                if (resultCode == Activity.RESULT_OK) {
                    if (data.getData() != null) {
                        Uri uri = data.getData();

                        OutputStream outputStream = cordova.getActivity().getContentResolver().openOutputStream(uri);
                        if (outputStream != null) {
                            // Write your content to the outputStream
                            String content = Chooser.FILE_CONTENT_MAIN;
                            outputStream.write(content.getBytes());
                            outputStream.close();

                            // Content has been written to the file
                        }
                        this.callback.error("Folder URI was null.");
                    } else {
                        this.callback.error("Folder URI was null.");
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    this.callback.error("RESULT_CANCELED");
                } else {
                    this.callback.error(resultCode);
                }
            }
        } catch (Exception err) {
            this.callback.error("Failed to read folder: " + err.toString());
        }
    }

    public JSONObject processFileUri(Uri uri) {
        ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
        String name = Chooser.getDisplayName(contentResolver, uri);
        String mediaType = contentResolver.getType(uri);
        if (mediaType == null || mediaType.isEmpty()) {
            mediaType = "application/octet-stream";
        }
        JSONObject file = new JSONObject();
        try {
            file.put("mediaType", mediaType);
            file.put("name", name);            ;
            file.put("uri", uri.toString());
        } catch (JSONException err) {
            this.callback.error("Processing failed: " + err.toString());
        }
        return file;
    } 
}
