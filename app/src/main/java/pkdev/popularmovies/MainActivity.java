package pkdev.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class MainActivity extends AppCompatActivity {

    final private String MOVIE_DB_API = "YOUR API KEY HERE";



    final private String[] MOVIE_SORTED_BY = {"popular", "top_rated"};
    final private String MOVIE_API_URL = "https://api.themoviedb.org/3/movie/%s?api_key=%s&page=%s";
    final private String MOVIE_IMG_URL = "http://image.tmdb.org/t/p/w185%s";

    final private static String TAG = MainActivity.class.getName();

    private int sorted_by = 0;
    private int last_page_num = 0;
    private FetchMovieDataTask fetch_movie_data_task = null;
    private ArrayAdapter<Movie> movie_adapter;
    private GridView movie_grid;

    final private int MIN_ITEMS = 10;

    private SharedPreferences app_setting;


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }


    @Override
    public void onResume() {
        super.onResume();

        int new_sorted_by = Integer.parseInt(app_setting.getString("sorted_by", "0"));
        if(new_sorted_by != sorted_by) {
            sorted_by = new_sorted_by;
            last_page_num = 0;
            if(fetch_movie_data_task != null) fetch_movie_data_task.skipped = true;
            fetch_movie_data_task = null;
            movie_adapter.clear();
            movie_adapter.notifyDataSetChanged();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        app_setting = PreferenceManager.getDefaultSharedPreferences(this);
        sorted_by = Integer.parseInt(app_setting.getString("sorted_by", "0"));

        final Context ctx = this;

        movie_grid = (GridView)this.findViewById(R.id.movie_grid);
        movie_adapter = new ArrayAdapter<Movie>(this, R.layout.movie_item) {

            class ViewHolder {
                ImageView img;
                TextView score;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                Movie m = getItem(position);

                if(convertView == null) {
                    convertView = LayoutInflater.from(ctx).inflate(R.layout.movie_item, parent, false);

                    ViewHolder holder = new ViewHolder();
                    holder.img = (ImageView)convertView.findViewById(R.id.imgv_movie);
                    holder.score = (TextView)convertView.findViewById(R.id.txtv_score);

                    convertView.setTag(holder);
                }

                ViewHolder holder = (ViewHolder)convertView.getTag();
                holder.score.setText(m.vote_average);
                Picasso.with(ctx).load(String.format(MOVIE_IMG_URL, m.poster_path)).into(holder.img);

                return convertView;
            }
        };
        movie_adapter.setNotifyOnChange(false);

        movie_grid.setOnScrollListener(
                new AbsListView.OnScrollListener() {
                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - MIN_ITEMS) {
                            Log.i(TAG, "Bottom Detected");
                            fetchMovieData();
                        }
                    }

                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                    }

                }
        );

        movie_grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Movie m = movie_adapter.getItem(position);

                Intent intent = new Intent(ctx, ScrollingActivity.class);
                intent.putExtra("movie", m);
                intent.putExtra("MOVIE_IMG_URL", MOVIE_IMG_URL);
                startActivity(intent);
            }
        });

        movie_grid.setAdapter(movie_adapter);

    }

    private void fetchMovieData() {
        if(fetch_movie_data_task != null && fetch_movie_data_task.getStatus() != AsyncTask.Status.FINISHED) return;

        Toast.makeText(this, String.format("Loading Page #%d", last_page_num + 1), Toast.LENGTH_SHORT).show();
        String url = String.format(MOVIE_API_URL, MOVIE_SORTED_BY[sorted_by], MOVIE_DB_API, last_page_num + 1);

        fetch_movie_data_task = new FetchMovieDataTask();
        fetch_movie_data_task.execute(url);
    }

    class FetchMovieDataTask extends AsyncTask<String, Void, Object[]> {
        private boolean skipped = false;

        protected Object[] doInBackground(String... params) {
            String url = params[0];

            try {
                Log.i(TAG, String.format("open url - %s", url));
                HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
                conn.setRequestProperty("Accept-Encoding", "gzip");

                String enc = conn.getContentEncoding();
                InputStream ins = conn.getInputStream();
                if(enc != null && enc.toLowerCase().indexOf("gzip") >= 0) {
                    Log.i(TAG, String.format("GZIP"));
                    ins = new GZIPInputStream(ins);
                }

                BufferedReader rd = new BufferedReader(new InputStreamReader(ins, "UTF-8"));
                StringBuilder result = new StringBuilder();
                String line;
                while((line = rd.readLine()) != null)
                    result.append(line);
                rd.close();

                Log.i(TAG, String.format("Parsing Json Object"));
                JSONObject obj = new JSONObject(result.toString());

                int page = obj.getInt("page");
                int total_pages = obj.getInt("total_pages");

                JSONArray results = obj.getJSONArray("results");
                final int len = Math.min(results.length(), 1000);

                Movie[] movies = new Movie[len];
                for(int i = 0; i < len; i++) {
                    Movie m = new Movie();
                    movies[i] = m;

                    JSONObject o = results.getJSONObject(i);
                    m.id = o.getInt("id");
                    m.title = o.getString("title");
                    m.overview = o.getString("overview");
                    m.release_date = o.getString("release_date");
                    m.vote_average = o.getString("vote_average");
                    m.poster_path = o.getString("poster_path");
                }

                return new Object[] {page, total_pages, movies};

            } catch(Exception e) {
                Log.w(TAG, e.toString());

            }

            return null;
        }

        protected void onPostExecute(Object[] result) {
            if(skipped || result == null || (int)result[0] != last_page_num + 1) return;

            Movie[] movies = (Movie[])result[2];

            last_page_num++;
            for(int i = 0; i < movies.length; i++) movie_adapter.add(movies[i]);

            if(last_page_num >= (int)result[1])
                fetch_movie_data_task = new FetchMovieDataTask();

            movie_adapter.notifyDataSetChanged();
        }

    };


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
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
