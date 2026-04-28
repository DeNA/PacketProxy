/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy.grpc

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FileDescriptor
import java.util.Collections

class GrpcServiceRegistry(fileDescriptors: List<FileDescriptor>) {
  private val inputByPath: Map<String, Descriptor>
  private val outputByPath: Map<String, Descriptor>
  private val messageByFullName: Map<String, Descriptor>

  init {
    val inputs = HashMap<String, Descriptor>()
    val outputs = HashMap<String, Descriptor>()
    val messages = HashMap<String, Descriptor>()
    for (fd in fileDescriptors) {
      indexMessages(fd.messageTypes, messages)
      for (service in fd.services) {
        for (method in service.methods) {
          val grpcPath = "/${service.fullName}/${method.name}"
          inputs[grpcPath] = method.inputType
          outputs[grpcPath] = method.outputType
        }
      }
    }
    inputByPath = Collections.unmodifiableMap(inputs)
    outputByPath = Collections.unmodifiableMap(outputs)
    messageByFullName = Collections.unmodifiableMap(messages)
  }

  fun getInputType(grpcPath: String?): Descriptor? {
    if (grpcPath == null) return null
    return inputByPath[grpcPath]
  }

  fun getOutputType(grpcPath: String?): Descriptor? {
    if (grpcPath == null) return null
    return outputByPath[grpcPath]
  }

  fun findMessageByName(fullName: String?): Descriptor? {
    if (fullName == null) return null
    return messageByFullName[fullName]
  }

  fun getServiceMethodEntries(): List<Pair<String, String>> {
    return inputByPath.keys
      .map { grpcPath ->
        val withoutLeading = grpcPath.removePrefix("/")
        val idx = withoutLeading.lastIndexOf('/')
        check(idx >= 0) { "invalid grpc path: $grpcPath" }
        val service = withoutLeading.substring(0, idx)
        val method = withoutLeading.substring(idx + 1)
        Pair(service, method)
      }
      .sortedWith(compareBy({ it.first }, { it.second }))
  }

  companion object {
    private fun indexMessages(types: Iterable<Descriptor>, out: MutableMap<String, Descriptor>) {
      for (d in types) {
        out[d.fullName] = d
        indexMessages(d.nestedTypes, out)
      }
    }
  }
}
