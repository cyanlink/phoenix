/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.phoenix.settings

import android.graphics.Bitmap
import androidx.annotation.UiThread
import androidx.lifecycle.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.receive.AmountTypingState
import fr.acinq.eclair.phoenix.receive.PaymentGenerationState
import fr.acinq.eclair.phoenix.receive.ReceiveViewModel
import fr.acinq.eclair.phoenix.utils.QRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class SettingsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SettingsViewModel::class.java)
}