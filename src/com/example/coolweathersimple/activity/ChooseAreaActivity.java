package com.example.coolweathersimple.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coolweathersimple.R;
import com.example.coolweathersimple.db.CoolWeatherDB;
import com.example.coolweathersimple.model.City;
import com.example.coolweathersimple.model.County;
import com.example.coolweathersimple.model.Province;
import com.example.coolweathersimple.util.HttpCallbackListener;
import com.example.coolweathersimple.util.HttpUtil;
import com.example.coolweathersimple.util.Utility;

public class ChooseAreaActivity extends Activity {

	public static final int LEVEL_PROVINCE = 0;
	public static final int LEVEL_CITY = 1;
	public static final int LEVEL_COUNTY = 2;
	
	private ProgressDialog progressDialog;
	private TextView titleText;
	private ListView listView;
	private ArrayAdapter<String> adapter;
	private CoolWeatherDB coolWeatherDB;
	private List<String> dataList = new ArrayList<String>();
//	=====================为省市县分别建立列表====================	
	/**
	 * 省列表
	 */
	private List<Province> provinceList;
	/**
	 * 市列表
	 */
	private List<City> cityList;
	/**
	 * 县列表
	 */
	private List<County> countyList;
//	================================选中的省份和城市==============================
	/**
	 * 选中的省份
	 */
	private Province selectedProvince;
	/**
	 * 选中的城市
	 */
	private City selectedCity;
//	================================选中的级别==============================
	/**
	 * 当前选中的级别
	 */
	private int currentLevel;
	/**
	 * 是否从WeatherActivity中跳转过来。
	 */
	private boolean isFromWeatherActivity;

	/**
	 * 控件实例,
	 * dB
	 * ListView及点击事件
	 * 加载省级数据
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		isFromWeatherActivity = super.getIntent().getBooleanExtra("from_weather_activity", false);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//		如果没有选中的,同时不是从WeatherActivity跳转过来的,返回WeatherActivity
		/*
		 * 处理异常
		 */
		if (prefs.getBoolean("city_selected", false) && !isFromWeatherActivity) {
			Intent intent = new Intent(this, WeatherActivity.class);
			this.startActivity(intent);
			this.finish();
			return;
		}
		super.requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.setContentView(R.layout.choose_area);
		
		listView = (ListView) findViewById(R.id.list_view);
		titleText = (TextView) findViewById(R.id.title_text);
		/*
		 * ListView:
		 */
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList);
		listView.setAdapter(adapter);
		/*
		 * dB连接
		 */
		coolWeatherDB = CoolWeatherDB.getInstance(this);
		
		/**
		 * ListView的点击事件:
		 * 根据
		 */
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int index,
					long arg3) {
//				根据不同的选中级别进行判断,一直到县一级,然后把数据传递回去
				if (currentLevel == LEVEL_PROVINCE) {
					selectedProvince = provinceList.get(index);
					ChooseAreaActivity.this.queryCities();
				} else if (currentLevel == LEVEL_CITY) {
					selectedCity = cityList.get(index);
					ChooseAreaActivity.this.queryCounties();
					
//					当选择到了县的时候,从List里面取出countyCode传递到WeatherActivity中
				} else if (currentLevel == LEVEL_COUNTY) {
					String countyCode = countyList.get(index).getCountyCode();					
					Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
					intent.putExtra("county_code", countyCode);
					ChooseAreaActivity.this.startActivity(intent);
					ChooseAreaActivity.this.finish();
				}
			}
		});
		this.queryProvinces();  // 加载省级数据
	};

	/**
	 * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryProvinces() {
		provinceList = coolWeatherDB.loadProvinces();
//		provinceList中存放的Province实例,从中取出province name 存放到dataList中
		if (provinceList.size() > 0) {
			dataList.clear();
			for (Province province : provinceList) {
				dataList.add(province.getProvinceName());
			}
//			通过ListView的adapter数据发生变化
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText("中国");
			currentLevel = LEVEL_PROVINCE;
		} else {
//			如果查不到就从服务器上查询
			this.queryFromServer(null, "province");
		}
	};

	/**
	 * 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryCities() {
		cityList = coolWeatherDB.loadCities(selectedProvince.getId());
		if (cityList.size() > 0) {
			dataList.clear();
			for (City city : cityList) {
				dataList.add(city.getCityName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedProvince.getProvinceName());
//			设置当前级别
			currentLevel = LEVEL_CITY;
		} else {
			queryFromServer(selectedProvince.getProvinceCode(), "city");
		}
	}
	
	/**
	 * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询。
	 */
	private void queryCounties() {
		countyList = coolWeatherDB.loadCounties(selectedCity.getId());
		if (countyList.size() > 0) {
			dataList.clear();
			for (County county : countyList) {
				dataList.add(county.getCountyName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedCity.getCityName());
			currentLevel = LEVEL_COUNTY;
		} else {
			queryFromServer(selectedCity.getCityCode(), "county");
		}
	}
	
	/**
	 * 根据传入的代号和类型从服务器上查询省市县数据。
	 * type:标志位,表示现在处理的是那个级别的数据
	 */
	private void queryFromServer(final String code, final String type) {
		String address;
		if (!TextUtils.isEmpty(code)) {
			address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
		} else {
			address = "http://www.weather.com.cn/data/list3/city.xml";
		}
		this.showProgressDialog();
		/*
		 * 访问服务器
		 */
		HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
//			回调接口中处理各种接收到的信息
			@Override
			public void onFinish(String response) {
				boolean result = false;
//				解析和处理各种服务器返回的数据,同时存储在数据库里面,
				if ("province".equals(type)) {
					result = Utility.handleProvincesResponse(coolWeatherDB,
							response);
				} else if ("city".equals(type)) {
					result = Utility.handleCitiesResponse(coolWeatherDB,
							response, selectedProvince.getId());
				} else if ("county".equals(type)) {
					result = Utility.handleCountiesResponse(coolWeatherDB,
							response, selectedCity.getId());
				}
//				再次调用了queryProvinces()来重新加载省级数据
//				因为涉及到了UI操作,必须在主线程中调用,使用runOnUiThread()方法来实现从子线程切换到了主线程
//				采用的同样是异步消息处理机制
				if (result) {
					// 通过runOnUiThread()方法回到主线程处理逻辑
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							closeProgressDialog();
							if ("province".equals(type)) {
								queryProvinces();
							} else if ("city".equals(type)) {
								queryCities();
							} else if ("county".equals(type)) {
								queryCounties();
							}
						}
					});
				}
			}

			@Override
			public void onError(Exception e) {
				// 通过runOnUiThread()方法回到主线程处理逻辑
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						closeProgressDialog();
						Toast.makeText(ChooseAreaActivity.this,
										"加载失败", Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
	}
	
	/**
	 * 显示进度对话框
	 */
	private void showProgressDialog() {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("正在加载...");
			progressDialog.setCanceledOnTouchOutside(false);
		}
		progressDialog.show();
	}
	
	/**
	 * 关闭进度对话框
	 */
	private void closeProgressDialog() {
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}
	
	/**
	 * 捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出。
	 */
	@Override
	public void onBackPressed() {
		if (currentLevel == LEVEL_COUNTY) {
			queryCities();
		} else if (currentLevel == LEVEL_CITY) {
			queryProvinces();
		} else {
			if (isFromWeatherActivity) {
				Intent intent = new Intent(this, WeatherActivity.class);
				startActivity(intent);
			}
			finish();
		}
	}

}