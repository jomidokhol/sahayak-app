package com.nur.sahayak.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.nur.sahayak.R
import com.nur.sahayak.adapters.NewsAdapter
import com.nur.sahayak.models.NewsItem
import com.nur.sahayak.utils.FirestoreSafeParser

class NewsFragment : Fragment() {

    private lateinit var rvNewsList: RecyclerView
    private lateinit var etNewsSearch: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmptyNews: TextView
    private lateinit var adapter: NewsAdapter

    private var allNews = mutableListOf<NewsItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)

        rvNewsList = view.findViewById(R.id.rvNewsList)
        etNewsSearch = view.findViewById(R.id.etNewsSearch)
        swipeRefresh = view.findViewById(R.id.swipeRefreshNews)
        tvEmptyNews = view.findViewById(R.id.tvEmptyNews)

        rvNewsList.layoutManager = LinearLayoutManager(context)
        adapter = NewsAdapter(allNews)
        rvNewsList.adapter = adapter

        fetchFirestoreNews()

        swipeRefresh.setOnRefreshListener {
            fetchFirestoreNews()
        }

        etNewsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    private fun fetchFirestoreNews() {
        swipeRefresh.isRefreshing = true
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("news_list").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                swipeRefresh.isRefreshing = false
                return@addSnapshotListener
            }

            allNews.clear()
            for (doc in snapshot.documents) {
                val item = NewsItem(
                    id = doc.id,
                    title = FirestoreSafeParser.parseString(doc.get("title")),
                    reporter = FirestoreSafeParser.parseString(doc.get("reporter")),
                    imageUrl = FirestoreSafeParser.parseString(doc.get("imageUrl")),
                    desc = FirestoreSafeParser.parseString(doc.get("desc")),
                    viewCount = FirestoreSafeParser.parseInt(doc.get("viewCount"), 0),
                    timestamp = FirestoreSafeParser.parseTimestampToMillis(doc.get("timestamp"))
                )
                allNews.add(item)
            }

            allNews.sortByDescending { it.timestamp }
            applySearchFilter()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun applySearchFilter() {
        val query = etNewsSearch.text.toString().trim()

        val filteredList = if (query.isEmpty()) {
            allNews
        } else {
            allNews.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.desc.contains(query, ignoreCase = true) ||
                        it.reporter.contains(query, ignoreCase = true)
            }
        }

        adapter.updateList(filteredList)

        if (filteredList.isEmpty()) {
            tvEmptyNews.visibility = View.VISIBLE
            rvNewsList.visibility = View.GONE
        } else {
            tvEmptyNews.visibility = View.GONE
            rvNewsList.visibility = View.VISIBLE
        }
    }
}
