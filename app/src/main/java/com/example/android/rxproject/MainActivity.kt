package com.example.android.rxproject

import android.Manifest.permission
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationRequest
import com.jakewharton.rxbinding2.view.RxView
import com.patloew.rxlocation.RxLocation
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private var disposable: Disposable? = null
    private var compositeDisposable: CompositeDisposable? = null
    private var rxLocation: RxLocation? = null
    private var locationRequest: LocationRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        setViews()
    }

    fun setViews() {
        rxLocation = RxLocation(this)
        rxLocation!!.setDefaultTimeout(15, TimeUnit.SECONDS)
        val rxPermissions = RxPermissions(this)
        rxPermissions.setLogging(true)
        compositeDisposable = CompositeDisposable()
        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(5000)

        disposable = RxView.clicks(fab)
            .compose(rxPermissions.ensureEach(permission.ACCESS_FINE_LOCATION))
            .subscribe({ permission ->
                if (permission.granted) {
                    startLocationRefresh()

                } else if (permission.shouldShowRequestPermissionRationale) {
                    Toast.makeText(this, "location permission denied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "location permission denied for ever", Toast.LENGTH_SHORT).show()
                }
            },
                { t ->
                    Toast.makeText(this, "location permission error: " + t.message, Toast.LENGTH_SHORT).show()
                },
                { Log.v("location_permission", " complete") })

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPlayServicesAvailable() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val status = apiAvailability.isGooglePlayServicesAvailable(this)

        if (status != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(status)) {
                apiAvailability.getErrorDialog(this, status, 1).show()
            } else {
                Toast.makeText(
                    this,
                    "Google Play Services unavailable. This app will not work",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun startLocationRefresh() {
        compositeDisposable!!.add(
            rxLocation!!.settings().checkAndHandleResolution(locationRequest!!)
                .flatMapObservable(this::getAddressObservable)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ onAddressUpdate(it) }, { t ->Log.e("MainActivity", "Error fetching location/address updates", t) })
        )
    }


    private fun getAddressObservable(success: Boolean): Observable<Address> {
        if (success) {
            return rxLocation!!.location().updates(locationRequest!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { run { onLocationUpdate(it) } }
                .flatMap { this.getAddressFromLocation(it) }

        } else {
            onLocationSettingsUnsuccessful()
            return rxLocation!!.location().lastLocation()
                .doOnSuccess { onLocationUpdate(it) }
                .flatMapObservable { this.getAddressFromLocation(it) }
        }
    }

    private fun getAddressFromLocation(location: Location): Observable<Address> {
        return rxLocation!!.geocoding().fromLocation(location).toObservable()
            .subscribeOn(Schedulers.io())
    }

    fun onLocationUpdate(location: Location) {
        val data = location.latitude.toString() + ", " + location.longitude
        location_text.setText(data)
    }

    fun onAddressUpdate(address: Address) {
    }

    fun onLocationSettingsUnsuccessful() {
        Toast.makeText(
            this,
            "Location settings requirements not satisfied. Showing last known location if available.",
            Toast.LENGTH_SHORT
        ).show()
    }


    override fun onDestroy() {
        if (disposable != null && disposable!!.isDisposed()) {
            disposable!!.dispose()
        }
        if (compositeDisposable != null && compositeDisposable!!.isDisposed()) {
            compositeDisposable!!.dispose()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        checkPlayServicesAvailable()
    }

}
