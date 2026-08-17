package com.nur.sahayak.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.ContactItem
import com.nur.sahayak.R
import com.nur.sahayak.adapters.ContactAdapter
import com.nur.sahayak.utils.FirestoreSafeParser

class ServicesFragment : Fragment() {

    companion object {
        var initialCategory: String = "all"
        var targetContactId: String = ""
    }

    private lateinit var rvContacts: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnFilterDropdown: MaterialButton
    private lateinit var llEmptyState: LinearLayout
    private lateinit var adapter: ContactAdapter

    private var allContacts = mutableListOf<ContactItem>()
    private var selectedCategory: String = "all"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_services, container, false)

        rvContacts = view.findViewById(R.id.rvContacts)
        etSearch = view.findViewById(R.id.etSearch)
        swipeRefresh = view.findViewById(R.id.swipeRefreshServices)
        btnFilterDropdown = view.findViewById(R.id.btnFilterDropdown)
        llEmptyState = view.findViewById(R.id.llEmptyState)

        rvContacts.layoutManager = LinearLayoutManager(context)
        adapter = ContactAdapter(allContacts)
        rvContacts.adapter = adapter

        checkAndApplyInitialCategory()
        fetchFirestoreContacts()

        swipeRefresh.setOnRefreshListener {
            fetchFirestoreContacts()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnFilterDropdown.setOnClickListener {
            showFilterDialog()
        }

        return view
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            checkAndApplyInitialCategory()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndApplyInitialCategory()
    }

    fun checkAndApplyInitialCategory() {
        if (initialCategory.isNotEmpty() && initialCategory != "all") {
            selectedCategory = initialCategory
            if (::btnFilterDropdown.isInitialized) {
                btnFilterDropdown.text = "ফিল্টার: ${getCategoryTitle(selectedCategory)} ▾"
            }
            initialCategory = "all"
            applyFilter()
        }
    }

    private fun showFilterDialog() {
        val categoriesMap = linkedMapOf(
            "all" to "সকল সেবা",
            "doctor" to "ডাক্তার",
            "hospital" to "হাসপাতাল",
            "police" to "পুলিশ স্টেশন",
            "fire" to "ফায়ার সার্ভিস",
            "mechanic" to "মেকানিক ও গ্যারেজ",
            "electronics" to "ইলেকট্রনিক্স",
            "mobile" to "মোবাইল সার্ভিস",
            "grocery" to "মুদি খানা",
            "pharmacy" to "ফার্মেসি",
            "diagnostic" to "ডায়াগনস্টিক",
            "computer" to "কম্পিউটার",
            "hotel" to "হোটেল",
            "restaurant" to "রেস্টুরেন্ট",
            "petrol" to "পেট্রোল পাম্প",
            "gas" to "গ্যাস সেবা",
            "ambulance" to "অ্যাম্বুলেন্স",
            "courier" to "কুরিয়ার",
            "other" to "অন্যান্য"
        )

        val titles = categoriesMap.values.toTypedArray()

        val dialogAdapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_1,
            titles
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.parseColor("#006A4E"))
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                view.setTypeface(null, Typeface.BOLD)
                view.setPadding(36, 28, 36, 28)
                return view
            }
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("সেবার ক্যাটাগরি নির্বাচন করুন")
            .setAdapter(dialogAdapter) { _, which ->
                val selectedKey = categoriesMap.keys.elementAt(which)
                val selectedTitle = titles[which]
                selectedCategory = selectedKey
                btnFilterDropdown.text = "ফিল্টার: $selectedTitle ▾"
                applyFilter()
            }
            .show()
    }

    private fun fetchFirestoreContacts() {
        swipeRefresh.isRefreshing = true
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("contacts").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                swipeRefresh.isRefreshing = false
                return@addSnapshotListener
            }

            allContacts.clear()
            for (doc in snapshot.documents) {
                val isApproved = FirestoreSafeParser.parseBoolean(doc.get("isApproved"), true)

                if (isApproved) {
                    val item = ContactItem(
                        id = doc.id,
                        category = FirestoreSafeParser.parseString(doc.get("category")),
                        name = FirestoreSafeParser.parseString(doc.get("name")),
                        title = FirestoreSafeParser.parseString(doc.get("title")),
                        phone = FirestoreSafeParser.parseString(doc.get("phone")),
                        location = FirestoreSafeParser.parseString(doc.get("location")),
                        whatsapp = FirestoreSafeParser.parseString(doc.get("whatsapp")),
                        facebook = FirestoreSafeParser.parseString(doc.get("facebook")),
                        isApproved = isApproved
                    )
                    allContacts.add(item)
                }
            }
            applyFilter()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun applyFilter() {
        val query = if (::etSearch.isInitialized) etSearch.text.toString().trim() else ""

        var filteredList = allContacts.filter { item ->
            val matchesCategory = if (selectedCategory == "all") true else item.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = query.isEmpty() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.location.contains(query, ignoreCase = true)

            matchesCategory && matchesQuery
        }

        // Put the target shared contact at the top if coming from Deep Link
        if (targetContactId.isNotEmpty()) {
            filteredList = filteredList.sortedByDescending { it.id == targetContactId }
            targetContactId = "" // Clear after applying
        }

        if (::adapter.isInitialized) {
            adapter.updateList(filteredList)
        }

        if (::llEmptyState.isInitialized && ::rvContacts.isInitialized) {
            if (filteredList.isEmpty()) {
                llEmptyState.visibility = View.VISIBLE
                rvContacts.visibility = View.GONE
            } else {
                llEmptyState.visibility = View.GONE
                rvContacts.visibility = View.VISIBLE
            }
        }
    }

    private fun getCategoryTitle(cat: String): String {
        return when (cat) {
            "doctor" -> "ডাক্তার"
            "hospital" -> "হাসপাতাল"
            "police" -> "পুলিশ স্টেশন"
            "fire" -> "ফায়ার সার্ভিস"
            "mechanic" -> "মেকানিক"
            "electronics" -> "ইলেকট্রনিক্স"
            "mobile" -> "মোবাইল সার্ভিস"
            "grocery" -> "মুদি খানা"
            "pharmacy" -> "ফার্মেসি"
            "diagnostic" -> "ডায়াগনস্টিক"
            "computer" -> "কম্পিউটার"
            "hotel" -> "হোটেল"
            "restaurant" -> "রেস্টুরেন্ট"
            "petrol" -> "পেট্রোল পাম্প"
            "gas" -> "গ্যাস সেবা"
            "ambulance" -> "অ্যাম্বুলেন্স"
            "courier" -> "কুরিয়ার"
            else -> "সকল সেবা"
        }
    }
}
