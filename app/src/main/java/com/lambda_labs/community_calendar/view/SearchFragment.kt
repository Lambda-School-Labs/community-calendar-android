package com.lambda_labs.community_calendar.view

import EventsQuery
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lambda_labs.community_calendar.R
import com.lambda_labs.community_calendar.adapter.RecentSearchRecyclerChild
import com.lambda_labs.community_calendar.model.Filter
import com.lambda_labs.community_calendar.model.Search
import com.lambda_labs.community_calendar.util.*
import com.lambda_labs.community_calendar.viewmodel.SearchViewModel
import com.lambda_labs.community_calendar.viewmodel.SharedFilterViewModel
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.searches_recycler_item.view.*
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class SearchFragment : Fragment() {

    private lateinit var mainActivity: MainActivity
    private lateinit var viewModel: SearchViewModel
    private lateinit var events: ArrayList<EventsQuery.Event>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = context as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private val searchBar: SearchView by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = get()

        events = ArrayList()
        // Network call through HomeViewMode
        viewModel.getAllEvents().observe(viewLifecycleOwner, Observer<List<EventsQuery.Event>> { list ->
            list.forEach { event ->
                events.add(event)
            }
        })

        if (searchBar.parent != null){
            (searchBar.parent as ViewGroup).removeView(searchBar)
        }
        search_layout.addView(searchBar)
        setSearchBarProperties(searchBar, false, topMargin = 0f, endMargin = 0f)

       viewModel.setupSearchBarConstraints(search_layout, searchBar, btn_cancel, btn_filters)

        // Navigates out of SearchFragment to previous fragment.
        // onDestroy has more logic to wrap this action up.
        btn_cancel.setOnClickListener {
            findNavController().navigateUp()
        }


        searches_recycler.setHasFixedSize(true)
        searches_recycler.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)

        viewModel.getRecentSearches()
        viewModel.searchList.observe(viewLifecycleOwner, Observer<MutableList<Search>> { recentSearches ->
            searches_recycler.adapter = RecentSearchRecycler(recentSearches)
        })

        btn_filters.setOnClickListener {
            hideKeyboard(mainActivity)
            this.findNavController().navigate(R.id.action_searchFragment_to_filterFragment)
        }

        // Instantiate the shared ViewModel to allow user selections to persist after fragment destruction
        val sharedFilterViewModel: SharedFilterViewModel = get()

        val filter: Filter? = sharedFilterViewModel.getSharedData().value
        // Populate the initial chip tags to be added to the included group
        val chipList = filter?.tags ?: arrayListOf()
        if (chipList.isNotEmpty()) chips.visibility = View.VISIBLE
        createChipLayout(chipList, mainActivity, chip_group_search)

        fun convertFilterToSearch(filter: Filter?): Search {
            var date = negativeDate()
            var tags = listOf("")
            var location = ""
            var zipcode = -1
            if (filter != null) {
                if (filter.date.isNotEmpty()) date = searchStringToDate(filter.date)
                if (filter.tags.isNotEmpty()) tags = filter.tags
                if (filter.location.isNotEmpty()) location = filter.location
                if (filter.zip.isNotEmpty()) zipcode = filter.zip.toInt()
            }
            return Search("", location, zipcode, date, tags)
        }
        val search = convertFilterToSearch(filter)

        // Search actions
        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null){
                    // Function connects to repository (see above function)
                    search.searchText = query
                    val filteredList = searchEvents(events, search)
                    viewModel.addRecentSearch(search)
                    val bundle = viewModel.createBundle(filteredList, search.searchText)
                    findNavController().navigate(R.id.searchResultFragment, bundle)
                }
                hideKeyboard(searchBar.context as MainActivity)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })




    }

    override fun onDestroy() {
        hideKeyboard(mainActivity)
        searchBar.clearFocus()
        searchBar.setQuery("", false)

        super.onDestroy()
    }

    inner class RecentSearchRecycler(private val searches: MutableList<Search>) :
        RecyclerView.Adapter<RecentSearchRecycler.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return position
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.searches_recycler_item, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = searches.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recentSearch = searches[position]

            val image = holder.drop

            holder.searchText.text = recentSearch.searchText

            val list = viewModel.searchToSearchList(recentSearch)

            val hasNoFilters = viewModel.hasNoFilters(recentSearch)
            if (hasNoFilters) holder.drop.visibility = View.GONE
            else holder.drop.visibility = View.VISIBLE

            holder.searchText.setOnClickListener {
                val filterEvents = searchEvents(events, recentSearch)
                val bundle = viewModel.createBundle(filterEvents, recentSearch.searchText)
                findNavController().navigate(R.id.searchResultFragment, bundle)
            }


            var clicked = 0
            image.setOnClickListener {
                val layoutManager = LinearLayoutManager(activity)
                holder.recyclerView.layoutManager = layoutManager
                holder.recyclerView.adapter = RecentSearchRecyclerChild(list)
                if (clicked == 0) {
                    holder.recyclerView.visibility = View.VISIBLE
                    image.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.drop_up))
                    clicked = 1
                }
                else {
                    holder.recyclerView.visibility = View.GONE
                    image.setImageDrawable(ContextCompat.getDrawable(mainActivity, R.drawable.drop_down))
                    clicked = 0
                }
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val searchText: TextView = view.search_txt
            val drop: ImageView = view.drop_down
            val recyclerView: RecyclerView = view.recycler_recent_search

        }
    }
}
