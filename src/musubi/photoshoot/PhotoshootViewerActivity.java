package musubi.photoshoot;

import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;

public class PhotoshootViewerActivity extends Activity {
    DbObj mAlbum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mAlbum = Musubi.forIntent(this, getIntent()).getObj();
        //Cursor c = mAlbum.getSubfeed().query("type=?", new String[] { "picture" } );
        //((TextView)findViewById(R.id.text)).setText(c.getCount() + " pictures.");
        // TODO: ListView or Gallery or ViewPager.
    }
}
