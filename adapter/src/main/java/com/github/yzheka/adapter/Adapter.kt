package com.github.yzheka.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.view.ContextThemeWrapper
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.core.util.forEach
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.paging.PositionalDataSource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager.widget.PagerAdapter
import kotlin.reflect.KProperty1

data class ItemInfo<B:ViewBinding,T>(
    val binding:B,
    val view:View,
    val item:T,
    val position:Int,
    val totalCount:Int
)

interface ItemBinding{
    fun onCreateView(inflater: LayoutInflater,parent:ViewGroup): View
    fun onBindView(view: View,item:Any?,position:Int, totalCount:Int)
    fun canBind(item:Any?,position: Int,totalCount: Int):Boolean
    fun onUnbindView(view: View)=Unit

    interface ViewBindingBuilder<B:ViewBinding,T>{
        fun onPrepare(prepare:(binding:B)->Unit):ViewBindingBuilder<B,T>
        fun onBind(onBind:(binding:B,item:T,position:Int,totalCount:Int)->Unit):ViewBindingBuilder<B,T>
        fun onUnbind(unbind:(B)->Unit):ViewBindingBuilder<B,T>
        fun canBind(predicate:(item: T,position: Int,totalCount: Int)->Boolean):ViewBindingBuilder<B,T>
        fun setOnViewClickListener(view:KProperty1<B,View>?=null,onClick:(ItemInfo<B,T>)->Unit):ViewBindingBuilder<B,T>
        fun setOnViewLongClickListener(view:KProperty1<B,View>?=null,onClick:(ItemInfo<B,T>)->Unit):ViewBindingBuilder<B,T>
        fun build():ItemBinding
    }

    interface DataBindingBuilder<B:ViewDataBinding,T>:ViewBindingBuilder<B,T>{
        fun setItemVariableId(id:Int):DataBindingBuilder<B,T>
        fun setPositionVariableId(id:Int):DataBindingBuilder<B,T>
        fun setTotalCountVariableId(id: Int):DataBindingBuilder<B,T>
        fun putExtra(variableId:Int,value:Any?):DataBindingBuilder<B,T>
        fun setLifecycleOwner(lifecycleOwner: LifecycleOwner?):DataBindingBuilder<B,T>

        override fun onUnbind(unbind: (B) -> Unit): DataBindingBuilder<B,T>
        override fun onPrepare(prepare: (binding: B) -> Unit): DataBindingBuilder<B,T>
        override fun onBind(onBind: (binding: B, item: T, position: Int, totalCount: Int) -> Unit): DataBindingBuilder<B, T>
        override fun canBind(predicate: (item: T, position: Int, totalCount: Int) -> Boolean): DataBindingBuilder<B, T>
        override fun setOnViewClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): DataBindingBuilder<B, T>
        override fun setOnViewLongClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): DataBindingBuilder<B, T>
    }

    companion object{
        inline fun <reified B:ViewBinding,reified T>viewBindingBuilder():ViewBindingBuilder<B,T> = object : ViewBindingBuilder<B,T>{
            private var mPrepare:(B)->Unit={}
            private var mBind:(B,T,Int,Int)->Unit={_,_,_,_->}
            private var mUnbind:(B)->Unit={}
            private var mCanBind:(T,Int,Int)->Boolean={_,_,_->true}
            private val mClickListeners= hashMapOf<KProperty1<B,View>?,(ItemInfo<B,T>)->Unit>()
            private val mLongClickListeners= hashMapOf<KProperty1<B,View>?,(ItemInfo<B,T>)->Unit>()

            override fun setOnViewClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): ViewBindingBuilder<B, T> = apply { mClickListeners[view]=onClick }
            override fun setOnViewLongClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): ViewBindingBuilder<B, T> = apply { mLongClickListeners[view]=onClick }
            override fun onPrepare(prepare: (binding: B) -> Unit): ViewBindingBuilder<B, T> = apply { mPrepare=prepare }
            override fun onBind(onBind: (binding: B, item: T, position: Int, totalCount: Int) -> Unit): ViewBindingBuilder<B, T> = apply { mBind=onBind }
            override fun onUnbind(unbind: (B) -> Unit): ViewBindingBuilder<B, T> = apply { mUnbind=unbind }
            override fun canBind(predicate: (item: T, position: Int, totalCount: Int) -> Boolean): ViewBindingBuilder<B, T> = apply { mCanBind=predicate }
            override fun build(): ItemBinding = object : ItemBinding{
                private val prepare=mPrepare
                private val bind=mBind
                private val unbind=mUnbind
                private val predicate=mCanBind
                private val clickListeners=HashMap(mClickListeners)
                private val longClickListeners=HashMap(mLongClickListeners)

                override fun canBind(item: Any?, position: Int, totalCount: Int): Boolean = item is T&&predicate(item,position, totalCount)

                override fun onUnbindView(view: View) {
                    val method=B::class.java.getDeclaredMethod("bind",View::class.java)
                    if(!method.isAccessible)method.isAccessible=true
                    val binding=method.invoke(null,view) as B
                    unbind(binding)
                }

                override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
                    val method=B::class.java.getDeclaredMethod("inflate",LayoutInflater::class.java,ViewGroup::class.java,Boolean::class.javaPrimitiveType)
                    if(!method.isAccessible)method.isAccessible=true
                    val binidng=method.invoke(null,inflater,parent,false) as B
                    prepare(binidng)
                    return binidng.root
                }

                override fun onBindView(view: View,item: Any?,position: Int,totalCount: Int) {
                    val method=B::class.java.getDeclaredMethod("bind",View::class.java)
                    if(!method.isAccessible)method.isAccessible=true
                    val binding=method.invoke(null,view) as B
                    val typedItem=item as T
                    clickListeners.forEach { entry->
                        val v=entry.key?.get(binding)?:binding.root
                        v.setOnClickListener{entry.value.invoke(ItemInfo(binding,it,typedItem,position,totalCount))}
                    }
                    longClickListeners.forEach { entry ->
                        val v=entry.key?.get(binding)?:binding.root
                        v.setOnLongClickListener {
                            entry.value.invoke(ItemInfo(binding,it,typedItem,position,totalCount))
                            true
                        }
                    }
                    bind(binding,typedItem,position,totalCount)
                }
            }
        }

        inline fun <reified B:ViewDataBinding,reified T> dataBindingBuilder():DataBindingBuilder<B,T> = object : DataBindingBuilder<B,T>{
            private var mPrepare:(B)->Unit={}
            private var mBind:(B,T,Int,Int)->Unit={_,_,_,_->}
            private var mUnbind:(B)->Unit={}
            private var mCanBind:(T,Int,Int)->Boolean={_,_,_->true}
            private var mItemVariableId=0
            private var mPositionVariableId=0
            private var mTotalCountVariableId=0
            private val mExtras=SparseArray<Any?>()
            private var mLifecycleOwner:LifecycleOwner?=null
            private val mClickListeners= hashMapOf<KProperty1<B,View>?,(ItemInfo<B,T>)->Unit>()
            private val mLongClickListeners= hashMapOf<KProperty1<B,View>?,(ItemInfo<B,T>)->Unit>()

            override fun onPrepare(prepare: (binding: B) -> Unit): DataBindingBuilder<B, T> = apply { mPrepare=prepare }
            override fun onBind(onBind: (binding: B, item: T, position: Int, totalCount: Int) -> Unit): DataBindingBuilder<B, T> = apply { mBind=onBind }
            override fun onUnbind(unbind: (B) -> Unit): DataBindingBuilder<B, T> = apply { mUnbind=unbind }
            override fun canBind(predicate: (item: T, position: Int, totalCount: Int) -> Boolean): DataBindingBuilder<B, T> = apply { mCanBind=predicate }
            override fun setItemVariableId(id: Int): DataBindingBuilder<B, T> = apply { mItemVariableId=id }
            override fun setPositionVariableId(id: Int): DataBindingBuilder<B, T> = apply { mPositionVariableId=id }
            override fun setTotalCountVariableId(id: Int): DataBindingBuilder<B, T> = apply { mTotalCountVariableId=id }
            override fun putExtra(variableId: Int, value: Any?): DataBindingBuilder<B, T> = apply { mExtras.put(variableId,value) }
            override fun setLifecycleOwner(lifecycleOwner: LifecycleOwner?): DataBindingBuilder<B, T> = apply { mLifecycleOwner=lifecycleOwner }
            override fun setOnViewClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): DataBindingBuilder<B, T> = apply { mClickListeners[view]=onClick }
            override fun setOnViewLongClickListener(view: KProperty1<B, View>?,onClick: (ItemInfo<B,T>) -> Unit): DataBindingBuilder<B, T> = apply { mLongClickListeners[view]=onClick }

            override fun build(): ItemBinding = object : ItemBinding{
                private val prepare=mPrepare
                private val bind=mBind
                private val unbind=mUnbind
                private val predicate=mCanBind
                private val itemVariableId=mItemVariableId
                private val positionVariableId=mPositionVariableId
                private val totalCountVariableId=mTotalCountVariableId
                private val extras=mExtras.clone()
                private val lifecycleOwner=mLifecycleOwner
                private val clickListeners=HashMap(mClickListeners)
                private val longClickListeners=HashMap(mLongClickListeners)

                override fun canBind(item: Any?, position: Int, totalCount: Int): Boolean = item is T&&predicate(item,position, totalCount)

                override fun onUnbindView(view: View) {
                    val method=B::class.java.getDeclaredMethod("bind",View::class.java)
                    if(!method.isAccessible)method.isAccessible=true
                    val binding=method.invoke(null,view) as B
                    unbind(binding)
                }

                override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
                    val method=B::class.java.getDeclaredMethod("inflate",LayoutInflater::class.java,ViewGroup::class.java,Boolean::class.javaPrimitiveType)
                    if(!method.isAccessible)method.isAccessible=true
                    val binidng=method.invoke(null,inflater,parent,false) as B
                    binidng.lifecycleOwner=lifecycleOwner
                    extras.forEach { key, value -> binidng.setVariable(key,value) }
                    prepare(binidng)
                    return binidng.root
                }

                override fun onBindView(view: View,item: Any?,position: Int,totalCount: Int) {
                    val method=B::class.java.getDeclaredMethod("bind",View::class.java)
                    if(!method.isAccessible)method.isAccessible=true
                    val binding=method.invoke(null,view) as B
                    val typedItem=item as T
                    clickListeners.forEach { entry->
                        val v=entry.key?.get(binding)?:binding.root
                        v.setOnClickListener{entry.value.invoke(ItemInfo(binding,it,typedItem,position,totalCount))}
                    }
                    longClickListeners.forEach { entry ->
                        val v=entry.key?.get(binding)?:binding.root
                        v.setOnLongClickListener {
                            entry.value.invoke(ItemInfo(binding,it,typedItem,position,totalCount))
                            true
                        }
                    }
                    binding.setVariable(itemVariableId,item)
                    binding.setVariable(positionVariableId,position)
                    binding.setVariable(totalCountVariableId,totalCount)
                    bind(binding,typedItem,position,totalCount)
                }
            }
        }
    }
}

@SuppressLint("DiffUtilEquals")
private object DefaultDiffCallback:DiffUtil.ItemCallback<Any?>(){
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean = oldItem==newItem
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean = oldItem.hashCode()==newItem.hashCode()
}

class RecyclerViewAdapter(itemCallback: DiffUtil.ItemCallback<Any?> = DefaultDiffCallback,private val itemBindings:Set<ItemBinding>):PagedListAdapter<Any,RecyclerViewAdapter.ViewHolder>(itemCallback){
    constructor(itemCallback: DiffUtil.ItemCallback<Any?> = DefaultDiffCallback,vararg itemBindings: ItemBinding):this(itemCallback,itemBindings.toSet())
    private val bindings= arrayListOf<ItemBinding>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(bindings[viewType],parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.onBindView(holder.itemView,currentList?.get(position),position,itemCount)

    override fun onViewRecycled(holder: ViewHolder) = holder.binding.onUnbindView(holder.itemView)

    override fun getItemViewType(position: Int): Int {
        val item=currentList?.get(position)
        val count=itemCount
        val binding=itemBindings.find { it.canBind(item,position,count) }?:throw IllegalArgumentException("No item binding found for $item")
        var type=bindings.indexOf(binding)
        if(type<0){
            type=bindings.size
            bindings.add(binding)
        }
        return type
    }

    fun submit(list:Iterable<*>?) = submit(list,null)

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("RestrictedApi")
    fun submit(list:Iterable<*>?, commitCallback: Runnable?){
        if(list is PagedList<*>)submitList(list as PagedList<Any>,commitCallback)
        val pagedList=PagedList.Builder(ListDataSource(list?.toList()?: emptyList()),10)
            .setNotifyExecutor(ArchTaskExecutor.getMainThreadExecutor())
            .setFetchExecutor(ArchTaskExecutor.getIOThreadExecutor())
            .build()
        submitList(pagedList as PagedList<Any>)
    }

    override fun submitList(pagedList: PagedList<Any>?) = submitList(pagedList,null)

    override fun submitList(pagedList: PagedList<Any>?, commitCallback: Runnable?) {
        runCatching {
            super.submitList(pagedList,commitCallback)
        }.onFailure {
            super.submitList(null,null)
            super.submitList(pagedList,commitCallback)
        }
    }

    class ViewHolder(val binding: ItemBinding,parent:ViewGroup):RecyclerView.ViewHolder(
        binding.onCreateView(LayoutInflater.from(parent.context),parent)
    )

    private class ListDataSource<T>(private val list: List<T>) : PositionalDataSource<T>() {
        override fun loadInitial(params: LoadInitialParams,callback: LoadInitialCallback<T>) {
            val totalCount = list.size
            val position = computeInitialLoadPosition(params, totalCount)
            val loadSize = computeInitialLoadSize(params, position, totalCount)
            val sublist = list.subList(position, position + loadSize)
            callback.onResult(sublist, position, totalCount)
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(
                list.subList(
                    params.startPosition,
                    params.startPosition + params.loadSize
                )
            )
        }
    }
}

class ViewPagerAdapter(private val itemBindings:Set<ItemBinding>):PagerAdapter(){
    constructor(vararg itemBindings: ItemBinding):this(itemBindings.toSet())
    private var mList= emptyList<Any?>()
    private var mTitleAdapter:(Any?,Int,Int)->CharSequence? = {_,_,_->null}
    private var mWidthAdapter:(Any?,Int,Int)->Float = {_,_,_->1f}
    override fun getCount(): Int = mList.size

    fun setPageTitleAdapter(titleAdapter:(item:Any?,position:Int,totalCount:Int)->CharSequence?){
        mTitleAdapter=titleAdapter
        notifyDataSetChanged()
    }

    fun setPageWidthAdapter(widthAdapter:(item:Any?,position:Int,totalCount:Int)->Float){
        mWidthAdapter=widthAdapter
        notifyDataSetChanged()
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean = `object` is Holder&&`object`.view==view

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemCount=count
        val item=mList[position]
        val binding=itemBindings.find { it.canBind(item,position,itemCount) }?:throw IllegalArgumentException("No item binding found for $item")
        val view=binding.onCreateView(LayoutInflater.from(container.context),container)
        container.addView(view)
        binding.onBindView(view,item,position,itemCount)
        return Holder(item,position,view,binding)
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val holder=`object` as? Holder? ?:return
        container.removeView(holder.view)
        holder.binding.onUnbindView(holder.view)
    }

    override fun getItemPosition(`object`: Any): Int {
        val holder=`object` as? Holder? ?: return POSITION_NONE
        val index=mList.indexOf(holder.item)
        return when{
            index<0-> POSITION_NONE
            index==holder.position-> POSITION_UNCHANGED
            else->index
        }
    }

    override fun getPageTitle(position: Int): CharSequence? = mTitleAdapter(mList[position],position,count)

    override fun getPageWidth(position: Int): Float = mWidthAdapter(mList[position],position,count)

    fun submitList(list:Iterable<*>?){
        mList=ArrayList(list?.toList()?: emptyList())
        notifyDataSetChanged()
    }

    private data class Holder(
        val item:Any?,
        val position:Int,
        val view: View,
        val binding:ItemBinding
    )
}

class SimpleArrayAdapter(context:Context,private val itemBindings:Set<ItemBinding>):ArrayAdapter<Any?>(context,0){
    private var bindings = arrayListOf<ItemBinding>()
    private val itemInflater=LayoutInflater.from(context)
    private var dropdownInflater:LayoutInflater?=null
    override fun getViewTypeCount(): Int{
        var count=bindings.size
        if(count>0)return count
        for(i in 0 until count)getItemViewType(i)
        count=bindings.size
        return if(count>0) count
        else itemBindings.size
    }

    override fun setDropDownViewTheme(theme: Resources.Theme?) {
        dropdownInflater = when (theme) {
            null, itemInflater.context.theme -> null
            else -> LayoutInflater.from(ContextThemeWrapper(context,theme))
        }
    }

    override fun getDropDownViewTheme(): Resources.Theme? = dropdownInflater?.context?.theme

    override fun getItemViewType(position: Int): Int {
        val item=this.getItem(position)
        val count= count
        val binding=itemBindings.find { it.canBind(item,position,count) }?:throw IllegalArgumentException("No item binding found for $item")
        var type=bindings.indexOf(binding)
        if(type<0){
            type=bindings.size
            bindings.add(binding)
        }
        return type
    }

    fun submitList(list:Iterable<Any?>?){
        clear()
        addAll(list?.toList()?:return)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = createView(itemInflater,position, convertView, parent)

    private fun createView(inflater: LayoutInflater,position: Int, convertView: View?, parent: ViewGroup):View{
        val itemType=getItemViewType(position)
        var view=convertView
        var binding=view?.getTag(R.id.tag_item_binidng) as? ItemBinding?
        val newBinding=bindings[itemType]
        if(binding!=newBinding){
            view?.let { binding?.onUnbindView(it) }
            binding=newBinding
            view=newBinding.onCreateView(inflater,parent)
        }
        binding.onBindView(view!!,getItem(position),position,count)
        view.setTag(R.id.tag_item_binidng,binding)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = createView(dropdownInflater?:itemInflater,position, convertView, parent)
}