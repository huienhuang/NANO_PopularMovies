package pkdev.popularmovies;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

public class ScrollingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        Movie m = (Movie)intent.getSerializableExtra("movie");
        String MOVIE_IMG_URL = intent.getStringExtra("MOVIE_IMG_URL");

        setTitle(m.title);

        ImageView img = (ImageView)findViewById(R.id.imgv_detail_img);
        Picasso.with(this).load(String.format(MOVIE_IMG_URL, m.poster_path)).into(img);

        ((TextView)findViewById(R.id.txtv_detail_date)).setText(m.release_date);
        ((TextView)findViewById(R.id.txtv_detail_score)).setText(m.vote_average);
        ((TextView)findViewById(R.id.txtv_detail_review)).setText(m.overview);

    }
}
