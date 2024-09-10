package com.example.adaptivewakeworddetectionapplication

import android.os.Parcel
import android.os.Parcelable

data class  DetectionResult(
    val topClassLabels: List<String>,
    val confidences: List<Float>,
    val modelUsed: String,
    val timestamp: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.createStringArrayList() ?: emptyList(),
        parcel.createFloatArray()?.toList() ?: emptyList(),
        parcel.readString() ?: "",
        parcel.readLong()
    )


    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStringList(topClassLabels)
        parcel.writeFloatArray(confidences.toFloatArray())
        parcel.writeString(modelUsed)
        parcel.writeLong(timestamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DetectionResult> {

        private const val TAG = "DetectionResult"

        override fun createFromParcel(parcel: Parcel): DetectionResult {
            return DetectionResult(parcel)
        }

        override fun newArray(size: Int): Array<DetectionResult?> {
            return arrayOfNulls(size)
        }
    }
}
