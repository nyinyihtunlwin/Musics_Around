package com.example.musicsaround.dj;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.musicsaround.R;
import com.example.musicsaround.Timer;
import com.example.musicsaround.Utilities;
import com.example.musicsaround.dj.ServerDeviceListFragment.DJFragmentListener;

public class DJActivity extends Activity implements ChannelListener,
		DJFragmentListener
{
	public final static int DJ_MODE = 15;

	public static final String TAG = "DJ Mode Activity";
	private WifiP2pManager manager;
	private boolean channelRetried = false;
	private boolean isWifiP2pEnabled = false;
	private BroadcastReceiver receiver = null;
	ProgressDialog progressDialog = null;

	private Timer timer;

	private CountDownTimer keepAliveTimer;
	// Keep the Wifi Alive every 5 seconds
	private static final int KEEPALIVE_INTERVAL = 5000;

	// critical component for Wi-fi Direct connectivity
	private Channel channel;

	private final IntentFilter intentFilter = new IntentFilter();

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dj);

		// need these intent filters to catch the Wi-fi direct events
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(this, getMainLooper(), null);

		TextView txt_time = (TextView) this.findViewById(R.id.txt_dj_time);

		// start a timer with 25 ms precision
		this.timer = new Timer(Timer.DEFAULT_TIMER_PRECISION);
		// asynchronous call to start a timer
		this.timer.startTimer();

		keepAliveTimer = new CountDownTimer(KEEPALIVE_INTERVAL,
				KEEPALIVE_INTERVAL)
		{

			@Override
			public void onTick(long millisUntilFinished)
			{
			}

			@Override
			public void onFinish()
			{
				enableWifi();
				keepAliveTimer.start();
			}
		};
	}

	/** register the BroadcastReceiver with the intent values to be matched */
	@Override
	public void onResume()
	{
		super.onResume();
		receiver = new ServerWiFiDirectBR(manager, channel, this);
		registerReceiver(receiver, intentFilter);

		// ***Start discovering right away!
		discoverDevices();
		keepAliveTimer.start();
	}

	public void enableWifi()
	{
		WifiManager wifiManager = (WifiManager) this
				.getSystemService(this.WIFI_SERVICE);

		wifiManager.setWifiEnabled(true);
	}

	/**
	 * UI to show the discovery process
	 */
	public void onInitiateDiscovery()
	{
		if (progressDialog != null && progressDialog.isShowing())
		{
			progressDialog.dismiss();
		}

		progressDialog = ProgressDialog.show(this, "Press back to cancel",
				"finding peers", true, true,
				new DialogInterface.OnCancelListener()
				{
					@Override
					public void onCancel(DialogInterface dialog)
					{
						// stop discovery
						manager.stopPeerDiscovery(channel,
								new WifiP2pManager.ActionListener()
								{
									@Override
									public void onFailure(int reason)
									{
										// Toast.makeText(DJActivity.this,
										// "Stopping Discovery Failed : " +
										// reason,
										// Toast.LENGTH_SHORT).show();
										Log.e(TAG,
												"Stopping Discovery Failed. Error Code is: "
														+ reason);
									}

									@Override
									public void onSuccess()
									{
										Log.d(TAG, "Discovery stopped.");
									}
								});
					}
				});
	}

	public void discoverDevices()
	{
		// TODO: need a better non-blocking UI to notify users we are
		// discovering
		// onInitiateDiscovery();

		// first turn on the wifi p2p
		enableWifi();

		channelRetried = false;

		manager.discoverPeers(channel, new WifiP2pManager.ActionListener()
		{
			@Override
			public void onSuccess()
			{
				// Toast.makeText(DJActivity.this,
				// "Discovery Initiated",
				// Toast.LENGTH_SHORT).show();

				Log.d(TAG, "Discovery Initiated.");
			}

			// if we failed, then stop the discovery and start again
			@Override
			public void onFailure(int reasonCode)
			{
				Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
				manager.stopPeerDiscovery(channel,
						new WifiP2pManager.ActionListener()
						{
							@Override
							public void onFailure(int reason)
							{
								// Toast.makeText(DJActivity.this,
								// "Stopping Discovery Failed : " + reason,
								// Toast.LENGTH_SHORT).show();
								Log.e(TAG,
										"Stopping Discovery Failed. Error Code is: "
												+ reason);
							}

							@Override
							public void onSuccess()
							{
								manager.discoverPeers(channel,
										new WifiP2pManager.ActionListener()
										{
											@Override
											public void onSuccess()
											{
												// Toast.makeText(DJActivity.this,
												// "Discovery Initiated",
												// Toast.LENGTH_SHORT)
												// .show();

												Log.d(TAG,
														"Discovery Initiated.");
											}

											@Override
											public void onFailure(int reasonCode)
											{
												Log.e(TAG,
														"Discovery Failed. Error Code is: "
																+ reasonCode);
											}
										});
							}
						});
			}
		});
	}

	@Override
	public void onPause()
	{
		super.onPause();
		unregisterReceiver(receiver);

		keepAliveTimer.cancel();
	}

	@Override
	public void onDestroy()
	{
		disconnect();

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.wifidirect_less, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.atn_direct_enable:
				if (manager != null && channel != null)
				{
					// Since this is the system wireless settings activity, it's
					// not going to send us a result. We will be notified by
					// WiFiDeviceBroadcastReceiver instead.
					Intent intent = new Intent();
					// jump to wi-fi Direct settings
					intent.setClassName("com.android.settings",
							"com.android.settings.Settings$WifiP2pSettingsActivity");

					startActivity(intent);
				}
				else
				{
					Log.e(TAG, "channel or manager is null");
				}
				return true;

			case R.id.atn_direct_discover:
				discoverDevices();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Remove all peers and clear all fields. This is called on
	 * BroadcastReceiver receiving a state change event. This is merely an UI
	 * update.
	 */
	public void resetDeviceList()
	{
		ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
				.findFragmentById(R.id.frag_djs_devices);

		if (fragmentList != null)
		{
			fragmentList.clearPeers();
		}
	}

	@Override
	public void onChannelDisconnected()
	{
		// we will try once more
		if (manager != null && !channelRetried)
		{
			Toast.makeText(this, "Wi-fi Direct Channel lost. Trying again...",
					Toast.LENGTH_LONG).show();
			resetDeviceList();

			channelRetried = true;
			manager.initialize(this, getMainLooper(), this);
		}
		else
		{
			Toast.makeText(
					this,
					"Wi-fi Direct Channel is still lost. Try disabling / re-enabling Wi-fi Direct in the P2P Settings.",
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void showDetails(WifiP2pDevice device)
	{
		// TODO: This is for debugging, showing the device details
		DJMusicFragment fragMusic = (DJMusicFragment) getFragmentManager()
				.findFragmentById(R.id.frag_dj_music);
	}

	@Override
	public void showInfo(WifiP2pInfo info)
	{
		DJMusicFragment fragMusic = (DJMusicFragment) getFragmentManager()
				.findFragmentById(R.id.frag_dj_music);

		if (info.isGroupOwner)
		{
			// fragMusic.setDebugText("I am the group owner.");
		}
		else
		{
			// fragMusic.setDebugText("I am not the group owner.");
		}
	}

	/*
	 * Cancel an ongoing connection in progress. We won't actually use this
	 * method all that much
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.example.musicsaround.dj.ServerDeviceListFragment.DJFragmentListener
	 * #cancelDisconnect()
	 */
	@Override
	public void cancelDisconnect()
	{
		/*
		 * A cancel abort request by user. Disconnect i.e. removeGroup if
		 * already connected. Else, request WifiP2pManager to abort the ongoing
		 * request
		 */
		if (manager != null)
		{
			Log.d(TAG, "Someone requested a cancel connect!");

			final ServerDeviceListFragment fragment = (ServerDeviceListFragment) getFragmentManager()
					.findFragmentById(R.id.frag_djs_devices);

			if (fragment.getDevice() == null
					|| fragment.getDevice().status == WifiP2pDevice.CONNECTED)
			{
				// we don't disconnect the whole group... it would be nice just
				// to disconnect that one guy
				// disconnect();
			}
			else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
					|| fragment.getDevice().status == WifiP2pDevice.INVITED
					|| fragment.getDevice().status == WifiP2pDevice.CONNECTED)
			{
				manager.cancelConnect(channel, new ActionListener()
				{
					@Override
					public void onSuccess()
					{
						// Toast.makeText(DJActivity.this,
						// "Aborting connection",
						// Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure(int reasonCode)
					{
						// Toast.makeText(
						// DJActivity.this,
						// "Connect abort request failed. Reason Code: "
						// + reasonCode, Toast.LENGTH_SHORT)
						// .show();
						Log.e(TAG,
								"Could not abort connection, the reason is: "
										+ reasonCode);
					}
				});
			}
		}
	}

	/*
	 * This is the main method to connect to a device through Wi-Fi Direct!
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.example.musicsaround.dj.ServerDeviceListFragment.DJFragmentListener
	 * #connect(android.net.wifi.p2p.WifiP2pConfig)
	 */
	@Override
	public void connect(WifiP2pConfig config)
	{
		if (manager == null)
		{
			return;
		}

		// in DJ mode, we want to become the group owner
		WifiP2pConfig newConfig = config;
		newConfig.groupOwnerIntent = DJ_MODE;

		manager.connect(channel, newConfig, new ActionListener()
		{
			@Override
			public void onSuccess()
			{
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
			}

			@Override
			public void onFailure(int reason)
			{
				Toast.makeText(DJActivity.this,
						"Connection failed. Retrying...", Toast.LENGTH_SHORT)
						.show();
				Log.e(TAG,
						"Wi-fi Direct connection failed. The error code is: "
								+ reason);
			}
		});
	}

	@Override
	public void disconnect()
	{
		if (manager == null)
		{
			return;
		}

		// TODO: why do we have to remove the whole group upon disconnect?
		// perhaps we only need to do so upon exiting DJ mode
		manager.removeGroup(channel, new ActionListener()
		{
			@Override
			public void onFailure(int reasonCode)
			{
				Log.e(TAG, "Disconnect failed. Reason is: " + reasonCode);

			}

			@Override
			public void onSuccess()
			{
				Toast.makeText(DJActivity.this, "Disconnected a device.",
						Toast.LENGTH_SHORT).show();
				Log.d(TAG, "Disconnected from a device.");
			}
		});
	}

	/**
	 * @param isWifiP2pEnabled
	 *            the isWifiP2pEnabled to set
	 */
	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled)
	{
		this.isWifiP2pEnabled = isWifiP2pEnabled;
	}

	public void playRemoteMusic(Uri musicFileURI, long startTime, int startPos)
	{
		ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
				.findFragmentById(R.id.frag_djs_devices);

		// convert URI to actual file path
		Uri filePathFromActivity = Uri.parse(Utilities.getRealPathFromUri(
				(Activity) this, musicFileURI));

		File audioFile = new File(filePathFromActivity.getPath());

		fragmentList.playMusicOnClients(audioFile, startTime, startPos);
	}

	public void playRemoteMusic(String musicFilePath, long startTime,
			int startPos)
	{
		ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
				.findFragmentById(R.id.frag_djs_devices);

		File audioFile = new File(musicFilePath);

		fragmentList.playMusicOnClients(audioFile, startTime, startPos);
	}

	public void stopRemoteMusic()
	{
		ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
				.findFragmentById(R.id.frag_djs_devices);
		fragmentList.stopMusicOnClients();
	}

	public Timer getTimer()
	{
		return timer;
	}
}
