/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.AppKitModel
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentLnurlWithdrawBinding
import fr.acinq.phoenix.receive.PaymentGenerationState
import fr.acinq.phoenix.utils.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option
import java.io.IOException

class LNUrlWithdrawFragment : DialogFragment() {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlWithdrawBinding
  private lateinit var appKit: AppKitModel
  private lateinit var model: LNUrlWithdrawViewModel
  private val args: LNUrlWithdrawFragmentArgs by navArgs()

  private lateinit var unitList: List<String>

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentLnurlWithdrawBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      appKit = ViewModelProvider(it).get(AppKitModel::class.java)
      model = ViewModelProvider(this).get(LNUrlWithdrawViewModel::class.java)
      mBinding.model = model

      context?.let {
        unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
        ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
          adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
          mBinding.amountUnit.adapter = adapter
        }
        val unit = Prefs.getCoinUnit(it)
        mBinding.amountUnit.setSelection(unitList.indexOf(unit.code()))
      }

    } ?: dismiss()
    model.url.value = args.url
  }

  override fun onStart() {
    super.onStart()

    mBinding.amountValue.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) = Unit

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        try {
          val unit = mBinding.amountUnit.selectedItem.toString()
          val amountInput = mBinding.amountValue.text.toString()
          mBinding.amountError.text = ""
          val fiat = Prefs.getFiatCurrency(context!!)
          val amount = if (unit == fiat) {
            Option.apply(Converter.convertFiatToMsat(context!!, amountInput))
          } else {
            Converter.string2Msat_opt(amountInput, unit)
          }
          if (amount.isDefined) {
            if (unit == fiat) {
              mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), context!!, withUnit = true))
            } else {
              mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(context!!, amount.get(), withUnit = true))
            }
          } else {
            throw RuntimeException("amount is undefined")
          }
        } catch (e: Exception) {
          mBinding.amountError.text = getString(R.string.lnurl_withdraw_error_amount)
        }
      }
    })

    model.url.value?.let {
      try {
        val url = HttpUrl.get(it.callback)
        mBinding.confirmButton.setOnClickListener { _ -> sendWithdrawToRemote(url.newBuilder().addEncodedQueryParameter("k1", it.walletIdentifier), it.description) }
        mBinding.serviceHost.text = Converter.html(getString(R.string.lnurl_withdraw_service_host_label, url.topPrivateDomain()))
        context?.let { ctx -> mBinding.amountValue.setText(Converter.printAmountRaw(it.maxWithdrawable, ctx)) }
      } catch (e: Exception) {
        log.error("error when reading lnurl-withdraw=$it")
        dismiss()
      }
    }
  }

  private fun handleRemoteError(message: String) {
    model.state.postValue(LNUrlWithdrawState.ERROR)
    mBinding.error.text = message
  }

  private fun extractAmount(): Option<MilliSatoshi> {
    val unit = mBinding.amountUnit.selectedItem
    return if (unit == null || context == null) {
      Option.empty()
    } else {
      val amount = mBinding.amountValue.text.toString()
      if (unit == Prefs.getFiatCurrency(context!!)) {
        Option.apply(Converter.convertFiatToMsat(context!!, amount))
      } else {
        Converter.string2Msat_opt(amount, unit.toString())
      }
    }
  }

  private fun sendWithdrawToRemote(urlBuilder: HttpUrl.Builder, description: String) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending callback call: ", exception)
      model.state.value = LNUrlWithdrawState.ERROR
      mBinding.error.text = getString(R.string.lnurl_withdraw_error_internal)
    }) {
      Wallet.hideKeyboard(context, mBinding.amountValue)
      if (model.state.value == LNUrlWithdrawState.INIT) {
        model.state.value = LNUrlWithdrawState.IN_PROGRESS
        val amount = extractAmount()
        if (amount.isEmpty) {
          handleRemoteError(getString(R.string.lnurl_withdraw_error_amount))
        } else {
          val pr = appKit.generatePaymentRequest(if (description.isBlank()) getString(R.string.receive_default_desc) else description, amount)
          val url = urlBuilder
            .addEncodedQueryParameter("pr", PaymentRequest.write(pr))
            .build()
          log.info("sending LNURL-withdraw request {}", url.toString())
          Wallet.httpClient.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
              log.error("remote error when sending lnurl-withdraw callback: ", e)
              handleRemoteError(getString(R.string.lnurl_withdraw_error_callback_remote, url.topPrivateDomain(), e.localizedMessage))
            }

            override fun onResponse(call: Call, response: Response) {
              try {
                val json = LNUrl.handleLNUrlRemoteResponse(response)
                log.debug("lnurl-withdraw remote responds with {}", json.toString(2))
                model.state.postValue(LNUrlWithdrawState.DONE)
                mBinding.success.text = getString(R.string.lnurl_withdraw_success, url.topPrivateDomain())
              } catch (e: Exception) {
                log.error("error in LNURL-withdraw callback remote: ", e)
                handleRemoteError(getString(R.string.lnurl_withdraw_error_callback_remote, url.topPrivateDomain(),
                  when (e) {
                    is JSONException -> getString(R.string.lnurl_withdraw_error_json)
                    is LNUrlRemoteError, is LNUrlRemoteFailure -> e.localizedMessage
                    else -> getString(R.string.lnurl_withdraw_error_generic)
                  }))
              }
            }
          })
        }

      }
    }



  }

}

enum class LNUrlWithdrawState {
  INIT, IN_PROGRESS, DONE, ERROR
}

class LNUrlWithdrawViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)

  val state = MutableLiveData(LNUrlWithdrawState.INIT)
  val url = MutableLiveData<LNUrlWithdraw>()

}
