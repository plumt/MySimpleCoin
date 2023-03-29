package com.yun.mysimplecoin.base

import androidx.lifecycle.MutableLiveData

class ListLiveData<T>: MutableLiveData<ArrayList<T>>() {

    init {
        value = arrayListOf()
    }

    fun sizes(): Int{
        return value!!.size
    }

    fun get(p: Int): T{
        return value!![p]
    }

    fun pos(p: Int) : String{
        return value!![p].toString()
    }

    fun add(item: T) {
        val items: ArrayList<T>? = value
        items!!.add(item)
        value = items
    }

    fun addAll(list: List<T>?) {
        val items: ArrayList<T>? = value
        items!!.addAll(list!!)
        value = items
    }

    fun clear(notify: Boolean) {
        val items: ArrayList<T>? = value
        items!!.clear()
        if (notify) {
            value = items
        }
    }

    fun remove(item: T) {
        val items: ArrayList<T>? = value
        items!!.remove(item)
        value = items
    }

    fun notifyChange() {
        val items: ArrayList<T>? = value
        value = items
    }

}