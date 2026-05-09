package com.garage.opener

import androidx.car.app.Screen
import androidx.car.app.Session

class GarageSession : Session() {
    override fun onCreateScreen(intent: android.content.Intent): Screen {
        return GarageScreen(carContext)
    }
}
