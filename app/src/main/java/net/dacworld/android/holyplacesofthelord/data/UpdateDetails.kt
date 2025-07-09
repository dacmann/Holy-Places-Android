package net.dacworld.android.holyplacesofthelord.data // Or your chosen package

data class UpdateDetails(
    val updateTitle: String,
    val messages: List<String> // A list of messages to show, each as a paragraph
)