/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.vault.metadata;

import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

public interface DeletedMessageMetadataVault {
    Publisher<Void> store(User user, DeletedMessageWithStorageInformation deletedMessage);

    Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName);

    Publisher<Void> remove(BucketName bucketName, User user, MessageId messageId);

    Publisher<StorageInformation> retrieveStorageInformation(User user, MessageId messageId);

    Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, User user);

    Publisher<BucketName> listRelatedBuckets();
}
