package de.uvwxy.soundfinder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import de.uvwxy.audio.AudioParameters;
import de.uvwxy.sensors.CompassReader;
import de.uvwxy.sensors.SensorReader.SensorResultCallback;
import de.uvwxy.sensors.location.GPSWIFIReader;
import de.uvwxy.sensors.location.LocationReader.LocationResultCallback;
import de.uvwxy.sensors.location.LocationReader.LocationStatusCallback;

public class SoundFinder extends Activity {
	private static final String EXTRA_LOCATION_LAT = "EXTRA_LOCATION_LAT";
	private static final String EXTRA_LOCATION_LON = "EXTRA_LOCATION_LON";
	private static final String EXTRA_LOCATION_ALT = "EXTRA_LOCATION_ALT";
	private static final String EXTRA_LOCATION_PRECISION = "EXTRA_LOCATION_PRECISION";
	private static final String EXTRA_LOCATION_MAX_DISTANCE = "EXTRA_LOCATION_MAX_DISTANCE";
	private static final String EXTRA_LOCATION_DESTINATION_RADIUS = "EXTRA_LOCATION_DESTINATION_RADIUS";

	private static final String TAG = "SOUNDFINDER";

	private int origHeight = -1;
	private TextView vBarDistance = null;
	private TextView vBarCorrectRight = null;
	private TextView vBarCorrectLeft = null;
	private TextView vBarErrorLeft = null;
	private TextView vBarErrorRight = null;
	private TextView tvInfo = null;
	private View hBar1 = null;
	private View hBar0 = null;
	private View vBar1 = null;
	private View vBar0 = null;
	private ToggleButton tbtnBearing = null;
	private SeekBar sbTest = null;
	private SeekBar sbDist = null;

	private int tvInfoClickCount = 0;

	private double locationPrecision = 15.0f;
	private double locationMaxDistance = 250.0f;
	private float loactionLastAccuracy = -1f;
	private double locationDestinationZoneRadius = 10f;

	private SoundPool pool = null;
	private AudioParameters noise = new AudioParameters();
	private AudioParameters beep = new AudioParameters();
	private AudioParameters error = new AudioParameters();
	private AudioParameters destinationZone = new AudioParameters();

	boolean isNotAccurateEnough = true;
	boolean useCompass = false;

	private GPSWIFIReader locationReader = null;
	private CompassReader compassReader = null;

	private Location locationLast = new Location("Dummy");
	private Location locationDestination = new Location("Dummy");

	private void initGUI() {
		vBarDistance = (TextView) findViewById(R.id.vBarDistance);
		vBarCorrectRight = (TextView) findViewById(R.id.vBarCorrectRight);
		vBarCorrectLeft = (TextView) findViewById(R.id.vBarCorrectLeft);
		vBarErrorLeft = (TextView) findViewById(R.id.vBarErrorLeft);
		vBarErrorRight = (TextView) findViewById(R.id.vBarErrorRight);
		hBar1 = (View) findViewById(R.id.hBar1);
		vBar1 = (View) findViewById(R.id.vBar1);
		vBar0 = (View) findViewById(R.id.vBar0);
		hBar0 = (View) findViewById(R.id.hBar0);
		tbtnBearing = (ToggleButton) findViewById(R.id.tbtnBearing);
		tvInfo = (TextView) findViewById(R.id.tvInfo);
		sbTest = (SeekBar) findViewById(R.id.sbTest);
		sbTest.setVisibility(View.INVISIBLE);
		sbDist = (SeekBar) findViewById(R.id.sbDist);
		sbDist.setVisibility(View.INVISIBLE);

		// distance bar to red until precision sufficient
		vBarDistance.setBackgroundColor(Color.rgb(0xff, 0x44, 0x44));
	}

	private void initClicks() {

		tbtnBearing.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					useCompass = true;
					Toast.makeText(getApplicationContext(), "Using Compass", Toast.LENGTH_SHORT).show();
				} else {
					useCompass = false;
					Toast.makeText(getApplicationContext(), "Using GPS Bearing", Toast.LENGTH_SHORT).show();
				}
			}
		});

		tvInfo.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				tvInfoClickCount++;
				if (tvInfoClickCount > 6) {
					sbTest.setVisibility(View.VISIBLE);
					sbDist.setVisibility(View.VISIBLE);
				}
			}
		});

		sbTest.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					applyBearingDiffToAudio(progress - 180);
				}
			}
		});
		sbDist.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					locationLast.setAccuracy(1);
					handleLocation(progress);
				}
			}
		});
	}

	LocationStatusCallback cbStatus = new LocationStatusCallback() {

		@Override
		public void status(Location l) {
			// TODO Auto-generated method stub

		}
	};

	LocationResultCallback cbResult = new LocationResultCallback() {

		@Override
		public void result(Location l) {
			if (l == null) {
				return;
			}

			double distance = l.distanceTo(locationDestination);

			loactionLastAccuracy = l.getAccuracy();
			locationLast = l;
			
			handleLocation(distance);
		}
	};

	private SensorResultCallback compassCallback = new SensorResultCallback() {
		@Override
		public void result(float[] f) {
			if (f == null || f.length < 3) {
				return;
			}

			float azimuth = lowPass(f, null)[0];

			if (loactionLastAccuracy <= locationPrecision && useCompass) {
				applyBearingDiffToAudio(azimuth);
			}
		}
	};

	private void handleLocation(double distance) {
		double factor = distance / locationMaxDistance;

		factor = factor > 1f ? 1f : factor;
		factor = factor < 0f ? 0f : factor;

		setHeight(vBarDistance, (int) (factor * origHeight));
		vBarDistance.setText("" + (int) distance + "m");

		if (loactionLastAccuracy > locationPrecision) {
			error.volumeLeft = 1.f;
			error.volumeRight = 1.f;
			error.setVolumeOn(pool);
			beep.muteOn(pool);
			noise.muteOn(pool);
			vBarDistance.setBackgroundColor(Color.rgb(0xff, 0x44, 0x44));
			tvInfo.setText("Location error to high:\n" + loactionLastAccuracy + " > " + locationPrecision);

		} else {
			error.muteOn(pool);
			if (!useCompass) {
				applyBearingDiffToAudio(locationLast.getBearing());
			}

			vBarDistance.setBackgroundColor(Color.rgb(0x33, 0xb5, 0xe5));

		}

		if (distance <= locationDestinationZoneRadius) {
			beep.muteOn(pool);
			noise.muteOn(pool);
			error.muteOn(pool);

			destinationZone.volumeLeft = 1f;
			destinationZone.volumeRight = 1f;
			destinationZone.setVolumeOn(pool);

		} else {
			destinationZone.muteOn(pool);
		}

		if (distance > 250) {
			beep.speed = 1f;
		} else {
			beep.speed = (float) (1f + (1f - (distance / locationMaxDistance))); // 1f - 2f when getting close
		}
		pool.setRate(beep.streamId, beep.speed);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_soundfinder);

		// -1 = loop
		// -1 = no accuracy waiting/limit
		locationReader = new GPSWIFIReader(this, -1, -1, cbStatus, cbResult, true, true);
		compassReader = new CompassReader(this, -1, compassCallback);
		initAudioStreams();

		Intent config = getIntent();
		locationDestination.setLatitude(config.getDoubleExtra(EXTRA_LOCATION_LAT, 0));
		locationDestination.setLongitude(config.getDoubleExtra(EXTRA_LOCATION_LON, 0));
		locationDestination.setAltitude(config.getDoubleExtra(EXTRA_LOCATION_ALT, 0));
		locationPrecision = config.getDoubleExtra(EXTRA_LOCATION_PRECISION, 10.0f);
		locationMaxDistance = config.getDoubleExtra(EXTRA_LOCATION_MAX_DISTANCE, 100.0f);
		locationDestinationZoneRadius = config.getDoubleExtra(EXTRA_LOCATION_DESTINATION_RADIUS, 10f);

		initGUI();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (locationReader != null) {
			locationReader.startReading();
		}
		if (compassReader != null) {
			compassReader.startReading();
		}

		initClicks();
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (pool != null) {
			pool.stop(noise.streamId);
			pool.stop(beep.streamId);
			pool.unload(noise.soundId);
			pool.unload(beep.soundId);
			//			pool.stop(error.streamId);
			pool.release();
		}

		if (locationReader != null) {
			locationReader.stopReading();
		}
		if (compassReader != null) {
			compassReader.stopReading();
		}
	}

	private void initAudioStreams() {
		pool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
		beep.soundId = pool.load(this, R.raw.sound_beep, 1);
		noise.soundId = pool.load(this, R.raw.sound_noise_static, 1);
		error.soundId = pool.load(this, R.raw.sound_error, 1);
		destinationZone.soundId = pool.load(this, R.raw.sound_destination, 1);

		beep.speed = 1f;
		error.speed = 0.6f;
		pool.setOnLoadCompleteListener(onLoadCompleteListener);
	}

	private OnLoadCompleteListener onLoadCompleteListener = new OnLoadCompleteListener() {

		@Override
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {

			if (sampleId == noise.soundId) {
				noise.playOn(pool);
				noise.muteOn(pool);
			}
			if (sampleId == beep.soundId) {
				beep.playOn(pool);
				beep.muteOn(pool);
			}
			if (sampleId == error.soundId) {
				error.playOn(pool);
			}
			if (sampleId == destinationZone.soundId) {
				destinationZone.playOn(pool);
				destinationZone.muteOn(pool);
			}
		}
	};

	private void applyBearingDiffToAudio(float bearing) {
		float targetBearing = locationLast.bearingTo(locationDestination);
		float diff = bearing - targetBearing;
		diff = diff < -180 ? 360f + diff : diff;
		diff = diff > 180 ? diff - 360f : diff;

		//TODO: fix diff 0-360
		float delta = diff;
		delta /= 180f;
		//		Log.d(TAG, "delta = " + delta);
		origHeight = vBar0.getHeight();
		float posDelta = Math.abs(delta);

		beep.volumeLeft = angleToVolumeLeft(diff);
		beep.volumeRight = angleToVolumeRight(diff);
		setHeight(vBarCorrectRight, (int) (origHeight * beep.volumeRight));
		setHeight(vBarCorrectLeft, (int) (origHeight * beep.volumeLeft));

		noise.volumeLeft = angleToVolumeErrorLeft(diff);
		noise.volumeRight = angleToVolumeErrorRight(diff);
		setHeight(vBarErrorRight, (int) (origHeight * noise.volumeRight));
		setHeight(vBarErrorLeft, (int) (origHeight * noise.volumeLeft));

		noise.setVolumeOn(pool);
		beep.setVolumeOn(pool);
		error.muteOn(pool);

		tvInfo.setText("Difference: " + (int) diff + "\nBearing: " + bearing + "\n Target Bearing: " + targetBearing + "\nAccuracy: " + loactionLastAccuracy);
	}

	private void setHeight(TextView t, int h) {
		RelativeLayout.LayoutParams params = (LayoutParams) t.getLayoutParams();
		params.height = h;
		t.setLayoutParams(params);
	}

	private float angleToVolumeErrorLeft(float a) {
		a += 180f;
		a = a < -180 ? 360f + a : a;
		a = a > 180 ? a - 360f : a;
		return angleToVolumeLeft(a);
	}

	private float angleToVolumeErrorRight(float a) {
		a += 180f;
		a = a < -180 ? 360f + a : a;
		a = a > 180 ? a - 360f : a;
		return angleToVolumeRight(a);
	}

	private float angleToVolumeRight(float angle) {
		return angleToVolumeLeft(angle * -1.0f);
	}

	private float angleToVolumeLeft(float angle) {
		if (angle > 180.0f) {
			return 0f;
		} else if (angle < -90.0f) {
			return 0f;
		} else if (angle >= 0) {
			return 1.0f - (angle / 180.0f);
		} else if (angle < 0) {
			return 1.0f - (angle / -90.0f);
		}

		return 0.0f;
	}

	static final float ALPHA = 0.15f;

	/**
	 * @see http 
	 *      ://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
	 * @see http 
	 *      ://developer.android.com/reference/android/hardware/SensorEvent.html
	 *      #values
	 */
	protected float[] lowPass(float[] input, float[] output) {
		if (output == null)
			return input;

		for (int i = 0; i < input.length; i++) {
			output[i] = output[i] + ALPHA * (input[i] - output[i]);
		}
		return output;
	}

	public static void findNode(Activity act, double lat, double lon, double alt, double precision, double zoneRadius, double maxDistance) {
		Intent intent = new Intent(act, SoundFinder.class);
		intent.putExtra(EXTRA_LOCATION_LAT, lat);
		intent.putExtra(EXTRA_LOCATION_LON, lon);
		intent.putExtra(EXTRA_LOCATION_ALT, alt);
		intent.putExtra(EXTRA_LOCATION_PRECISION, precision);
		intent.putExtra(EXTRA_LOCATION_DESTINATION_RADIUS, zoneRadius);
		intent.putExtra(EXTRA_LOCATION_MAX_DISTANCE, maxDistance);
		try {
			act.startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
