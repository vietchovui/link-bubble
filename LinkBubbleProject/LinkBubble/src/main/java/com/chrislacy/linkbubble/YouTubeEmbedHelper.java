package com.chrislacy.linkbubble;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import com.squareup.picasso.Picasso;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class YouTubeEmbedHelper {

    private Context mContext;
    private List<String> mEmbedIds = new ArrayList<String>();

    private static class EmbedInfo {
        String mTitle;
        String mThumbnailUrl;
        String mId;
    }
    private List<EmbedInfo> mEmbedInfo = new ArrayList<EmbedInfo>();

    ResolveInfo mYouTubeResolveInfo;

    YouTubeEmbedHelper(Context context) {
        mContext = context;
        mYouTubeResolveInfo = Settings.get().getYouTubeViewResolveInfo();
    }

    void clear() {
        mEmbedIds.clear();
    }

    int size() {
        return mEmbedIds.size();
    }

    /*
     * Known YouTube embed URLs:
        * http://www.youtube.com/embed/oSAW1tSNIa4?version=3&rel=1&fs=1&showsearch=0&showinfo=1&iv_load_policy=1&wmode=transparent
        * https://www.youtube.com/embed/q1dpQKntj_w
     */
    boolean onYouTubeEmbedFound(String src) {
        if (src == null || src.isEmpty()) {
            return false;
        }

        int prefixStartIndex = src.indexOf(Config.YOUTUBE_EMBED_PREFIX);
        if (prefixStartIndex > -1) {
            URL url;
            try {
                url = new URL(src);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return false;
            }

            String path = url.getPath();
            int pathStartIndex = path.indexOf(Config.YOUTUBE_EMBED_PATH_SUFFIX);
            if (pathStartIndex > -1) {
                String videoId = path.substring(pathStartIndex + Config.YOUTUBE_EMBED_PATH_SUFFIX.length());
                if (videoId.length() > 0) {
                    boolean onList = false;
                    if (mEmbedIds.size() > 0) {
                        for (String s : mEmbedIds) {
                            if (s.equals(videoId)) {
                                onList = true;
                                break;
                            }
                        }
                    }
                    if (onList == false) {
                        mEmbedIds.add(videoId);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean loadYouTubeVideo(String id) {
        if (mYouTubeResolveInfo != null) {
            return MainApplication.loadIntent(mContext, mYouTubeResolveInfo.activityInfo.packageName,
                    mYouTubeResolveInfo.activityInfo.name, Config.YOUTUBE_WATCH_PREFIX + id, -1);
        }

        return false;
    }

    boolean onOpenInAppButtonClick() {
        int size = mEmbedIds.size();
        if (size == 1) {
            return loadYouTubeVideo(mEmbedIds.get(0));
        } else if (size > 1) {
            getMultipleEmbedsDialog().show();
            return true;
        }

        return false;
    }

    private AlertDialog getMultipleEmbedsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.view_loading, null);

        TextView textView = (TextView) view.findViewById(R.id.loading_text);
        textView.setText(R.string.loading_youtube_embed_info);

        builder.setView(view);
        builder.setIcon(0);

        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

        new DownloadYouTubeEmbedInfo(alertDialog).execute(null, null, null);

        return alertDialog;
    }

    //https://www.googleapis.com/youtube/v3/videos?id=7lCDEYXw3mM,CevxZvSJLk8&key=AIzaSyChiS6yef7AIe5p0JvJGnHrHmmimehIuDs&part=snippet&fields=items(snippet(title,thumbnails(default)))

    private class DownloadYouTubeEmbedInfo extends AsyncTask<Void, Void, Void> {
        private AlertDialog mLoadingAlertDialog;

        DownloadYouTubeEmbedInfo(AlertDialog loadingAlertDialog) {
            super();
            mLoadingAlertDialog = loadingAlertDialog;
        }

        protected Void doInBackground(Void... arg) {

            String idsAsString = "";
            for (String id : mEmbedIds) {
                if (idsAsString.length() > 0) {
                    idsAsString += ",";
                }
                idsAsString += id;
            }

            String url = "https://www.googleapis.com/youtube/v3/videos?id=" + idsAsString + "&key=" + Config.YOUTUBE_API_KEY +
                    "&part=snippet&fields=items(id,snippet(title,thumbnails(default)))";

            String jsonAsString = Util.downloadJSONAsString(url, 5000);

            mEmbedInfo.clear();

            try {
                JSONObject jsonObject = new JSONObject(jsonAsString);
                Object itemsObject = jsonObject.get("items");
                if (itemsObject instanceof JSONArray) {
                    JSONArray jsonArray = (JSONArray)itemsObject;
                    for (int i = 0; i < jsonArray.length(); ++i) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        EmbedInfo embedInfo = new EmbedInfo();
                        embedInfo.mId = item.getString("id");
                        JSONObject snippet = item.getJSONObject("snippet");
                        if (snippet != null) {
                            embedInfo.mTitle = snippet.getString("title");
                            JSONObject thumbnails = snippet.getJSONObject("thumbnails");
                            if (thumbnails != null) {
                                JSONObject defaultEntry = thumbnails.getJSONObject("default");
                                if (defaultEntry != null) {
                                    embedInfo.mThumbnailUrl = defaultEntry.getString("url");
                                    mEmbedInfo.add(embedInfo);
                                }
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(Void result) {
            if (mEmbedInfo.size() > 0) {
                mLoadingAlertDialog.dismiss();
                mLoadingAlertDialog = null;

                ListView listView = new ListView(mContext);
                listView.setAdapter(new EmbedItemAdapter());

                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setView(listView);
                builder.setIcon(mYouTubeResolveInfo.loadIcon(mContext.getPackageManager()));
                builder.setTitle(R.string.title_youtube_embed_to_load);

                final AlertDialog alertDialog = builder.create();
                alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                alertDialog.show();

                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        EmbedInfo embedInfo = (EmbedInfo) view.getTag();
                        if (embedInfo != null) {
                            loadYouTubeVideo(embedInfo.mId);
                        }
                        alertDialog.dismiss();
                    }
                });
            }
        }
    };


    private class EmbedItemAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mEmbedInfo.size();
        }

        @Override
        public Object getItem(int position) {
            return mEmbedInfo.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.view_youtube_embed_item, null);
            }

            EmbedInfo embedInfo = mEmbedInfo.get(position);

            ImageView imageView = (ImageView)convertView.findViewById(R.id.image);
            Picasso.with(mContext).load(embedInfo.mThumbnailUrl).into(imageView);

            TextView textView = (TextView)convertView.findViewById(R.id.text);
            textView.setText(embedInfo.mTitle);

            convertView.setTag(embedInfo);

            return convertView;
        }
    }
}
