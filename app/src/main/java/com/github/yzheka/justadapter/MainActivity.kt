package com.github.yzheka.justadapter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.github.yzheka.adapter.ItemBinding
import com.github.yzheka.adapter.RecyclerViewAdapter
import com.github.yzheka.adapter.SimpleArrayAdapter
import com.github.yzheka.adapter.ViewPagerAdapter
import com.github.yzheka.justadapter.databinding.ActivityMainBinding
import com.github.yzheka.justadapter.databinding.ViewItemBinding

class MainActivity : AppCompatActivity() {
    private val items= listOf(
        "Item 1",
        "Item 2",
        3,
        4.0,
        5f
    )

    private val stringItemBinding=ItemBinding.ViewBindingBuilder.create<ViewItemBinding,String>()
        .onPrepare { it.root.alpha=0.5f }
        .onBind { binding, item, _, _ -> binding.text.text = item }
        .setOnViewClickListener(ViewItemBinding::text){ Toast.makeText(it.view.context,it.item,Toast.LENGTH_SHORT).show() }
        .build()

    private val intItemBinding=ItemBinding.ViewBindingBuilder.create<ViewItemBinding,Int>()
        .onBind { binding, item, _, _ -> binding.text.text="Item $item" }
        .setOnViewClickListener { Toast.makeText(it.view.context,it.item.toString(),Toast.LENGTH_SHORT).show()}
        .build()

    private val doubleItemBinding=ItemBinding.ViewBindingBuilder.create<ViewItemBinding,Double>()
        .onBind { binding, item, _, _ -> binding.text.text="Item $item" }
        .build()

    private val floatItemBinding=ItemBinding.ViewBindingBuilder.create<ViewItemBinding,Float>()
        .onBind { binding, item, _, _ -> binding.text.text="Item $item" }
        .build()

    private val bindings=setOf(stringItemBinding,intItemBinding,doubleItemBinding,floatItemBinding)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding=ActivityMainBinding.inflate(layoutInflater)
        binding.listView.adapter=SimpleArrayAdapter(this,bindings).also { it.submitList(items) }
        binding.pager.adapter=ViewPagerAdapter(bindings).also { it.submitList(items) }
        binding.recyclerView.adapter=RecyclerViewAdapter(itemBindings = bindings).also { it.submitList(items) }
        setContentView(binding.root)
    }
}
