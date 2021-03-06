package de.luhmer.owncloudnewsreader.async_tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;

import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.luhmer.owncloudnewsreader.R;
import de.luhmer.owncloudnewsreader.SettingsActivity;
import de.luhmer.owncloudnewsreader.database.model.Feed;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.helper.ImageHandler;

import static de.luhmer.owncloudnewsreader.helper.ThemeChooser.THEME;
import static de.luhmer.owncloudnewsreader.helper.ThemeChooser.getInstance;


public class RssItemToHtmlTask extends AsyncTask<Void, Void, String> {

    private static final double BODY_FONT_SIZE = 1.1;
    private static final double HEADING_FONT_SIZE = 1.1;
    private static final double SUBSCRIPT_FONT_SIZE = 0.7;
    private static final String TAG = RssItemToHtmlTask.class.getCanonicalName();

    private static Pattern PATTERN_PRELOAD_VIDEOS_REMOVE = Pattern.compile("(<video[^>]*)(preload=\".*?\")(.*?>)");
    private static Pattern PATTERN_PRELOAD_VIDEOS_INSERT = Pattern.compile("(<video[^>]*)(.*?)(.*?>)");
    private static Pattern PATTERN_AUTOPLAY_VIDEOS_1 = Pattern.compile("(<video[^>]*)(autoplay=\".*?\")(.*?>)");
    private static Pattern PATTERN_AUTOPLAY_VIDEOS_2 = Pattern.compile("(<video[^>]*)(\\sautoplay)(.*?>)");
    private static Pattern PATTERN_AUTOPLAY_REGEX_CB = Pattern.compile("(.*?)^(Unser Feedsponsor:\\s*<\\/p><p>\\s*.*?\\s*<\\/p>)(.*)", Pattern.MULTILINE);


    private Context mContext;
    private RssItem mRssItem;
    private Listener mListener;


    public interface Listener {
        /**
         * The RSS item has successfully been parsed.
         * @param htmlPage  RSS item as HTML string
         */
        void onRssItemParsed(String htmlPage);
    }


    public RssItemToHtmlTask(Context context, RssItem rssItem, Listener listener) {
        this.mContext = context;
        this.mRssItem = rssItem;
        this.mListener = listener;
    }

    @Override
    protected String doInBackground(Void... params) {
        return getHtmlPage(mContext, mRssItem, true);
    }

    @Override
    protected void onPostExecute(String htmlPage) {
        mListener.onRssItemParsed(htmlPage);
        super.onPostExecute(htmlPage);
    }


    /**
     * @param context
     * @param rssItem       item to parse
     * @param showHeader    true if a header with item title, feed title, etc. should be included
     * @return given RSS item as full HTML page
     */
    public static String getHtmlPage(Context context, RssItem rssItem, boolean showHeader) {
        String feedTitle = "Undefined";
        String favIconUrl = null;

        Feed feed = rssItem.getFeed();
        //int[] colors = ColorHelper.getColorsFromAttributes(context,
        //        R.attr.dividerLineColor,
        //        R.attr.rssItemListBackground);

        //int feedColor = colors[0];
        if (feed != null) {
            feedTitle = Html.escapeHtml(feed.getFeedTitle());
            favIconUrl = feed.getFaviconUrl();
            //if(feed.getAvgColour() != null) {
            //    feedColor = Integer.parseInt(feed.getAvgColour());
            //}
        }

        if (favIconUrl != null) {
            DiskCache diskCache = ImageLoader.getInstance().getDiskCache();
            File file = diskCache.get(favIconUrl);
            if(file != null) {
                favIconUrl = "file://" + file.getAbsolutePath();
            }
        } else {
            favIconUrl = "file:///android_res/drawable/default_feed_icon_light.png";
        }

        String body_id = null;
        THEME selectedTheme = getInstance(context).getSelectedTheme();
        switch (selectedTheme) {
            case LIGHT:
                body_id = "lightTheme";
                break;
            case DARK:
                body_id = "darkTheme";
                break;
            case OLED:
                body_id = "darkThemeOLED";
                break;
        }

        Log.v(TAG, "Selected Theme: " + body_id);

        boolean isRightToLeft = context.getResources().getBoolean(R.bool.is_right_to_left);
        String rtlClass = isRightToLeft ? "rtl" : "";
        //String borderSide = isRightToLeft ? "right" : "left";

        StringBuilder builder = new StringBuilder();

        builder.append("<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=0\" />");
        builder.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"web.css\" />");


        // font size scaling
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        double scalingFactor = Float.parseFloat(mPrefs.getString(SettingsActivity.SP_FONT_SIZE, "1.0"));
        DecimalFormat fontFormat = new DecimalFormat("#.#");

        builder.append("<style type=\"text/css\">");
        builder.append(String.format(
                        ":root { \n" +
                            "--fontsize-body: %sem; \n" +
                            "--fontsize-header: %sem; \n" +
                            "--fontsize-subscript: %sem; \n" +
                        "}",
                fontFormat.format(scalingFactor*BODY_FONT_SIZE),
                fontFormat.format(scalingFactor*HEADING_FONT_SIZE),
                fontFormat.format(scalingFactor*SUBSCRIPT_FONT_SIZE)
            ));
        builder.append("</style>");



        builder.append(String.format("</head><body class=\"%s\" class=\"%s\">", body_id, rtlClass));

        if (showHeader) {
            builder.append("<div id=\"top_section\">");
            builder.append(String.format("<div id=\"header\" class=\"%s\">", body_id));
            String title = Html.escapeHtml(rssItem.getTitle());
            String linkToFeed = Html.escapeHtml(rssItem.getLink());
            builder.append(String.format("<a href=\"%s\">%s</a>", linkToFeed, title));
            builder.append("</div>");

            String authorOfArticle = Html.escapeHtml(rssItem.getAuthor());
            if (authorOfArticle != null)
                if (!authorOfArticle.trim().equals(""))
                    feedTitle += " - " + authorOfArticle.trim();

            builder.append("<div id=\"header_small_text\">");

            builder.append("<div id=\"subscription\">");
            builder.append(String.format("<img id=\"imgFavicon\" src=\"%s\" />", favIconUrl));
            builder.append(feedTitle.trim());
            builder.append("</div>");

            Date date = rssItem.getPubDate();
            if (date != null) {
                String dateString = (String) DateUtils.getRelativeTimeSpanString(date.getTime());
                builder.append("<div id=\"datetime\">");
                builder.append(dateString);
                builder.append("</div>");
            }

            builder.append("</div>");
            builder.append("</div>");
        }

        String description = rssItem.getBody();
        description = getDescriptionWithCachedImages(description).trim();
        description = replacePatternInText(PATTERN_PRELOAD_VIDEOS_REMOVE, description, "$1 $3"); // remove whatever preload is there
        description = replacePatternInText(PATTERN_PRELOAD_VIDEOS_INSERT, description, "$1 preload=\"metadata\" $3"); // add preload attribute
        description = replacePatternInText(PATTERN_AUTOPLAY_VIDEOS_1, description, "$1 $3");
        description = replacePatternInText(PATTERN_AUTOPLAY_VIDEOS_2, description, "$1 $3");

        //description = replacePatternInText(PATTERN_AUTOPLAY_REGEX_CB, description, "$1 $3");

        builder.append("<div id=\"content\">");
        builder.append(description);
        builder.append("</div>");

        builder.append("</body></html>");

        return builder.toString().replaceAll("\"//", "\"https://");
    }

    private static String getDescriptionWithCachedImages(String text) {
        List<String> links = ImageHandler.getImageLinksFromText(text);
        DiskCache diskCache = ImageLoader.getInstance().getDiskCache();

        for(String link : links) {
            link = link.trim();
            try {
                File file = diskCache.get(link);
                if(file != null)
                    text = text.replace(link, "file://" + file.getAbsolutePath());
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }

        return text;
    }

    private static String replacePatternInText(Pattern pattern, String text, String replacement) {
        Matcher m = pattern.matcher(text);
        return m.replaceAll(replacement);
    }
}
