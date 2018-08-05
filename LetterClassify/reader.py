import tensorflow as tf
import numpy as np


filename_queue = tf.train.string_input_producer(['/Users/Tracy/Documents/FCF/LetterClassify/dataTest/training.tfrecord'], num_epochs=None, shuffle=True)
reader = tf.TFRecordReader()
_, serialized_example = reader.read(filename_queue)
#features = tf.parse_single_example(serialized_example,
#                                   features={
#                                       'image': tf.FixedLenFeature([], tf.string),
#                                       'label': tf.FixedLenFeature([], tf.int64)
#                                   }
#
#                                    )
feature = {'image': tf.FixedLenFeature([], tf.string),
            'label': tf.FixedLenFeature([], tf.int64)}
features = tf.parse_single_example(serialized_example, features=feature)
#img = tf.decode_raw(features['image'], np.float32)
img = features['image']
img = tf.image.decode_png(img, channels=1)
img = tf.reshape(img, [28, 28, 1])
#label = features['label']
label = tf.cast(features['label'], tf.int32)

X_batch, y_batch = tf.train.shuffle_batch([img, label], batch_size=1,
                                          capacity=2, min_after_dequeue=1, num_threads=3)
sess = tf.Session()
init = tf.global_variables_initializer()
sess.run(init)

coord = tf.train.Coordinator()
threads = tf.train.start_queue_runners(sess=sess, coord=coord)
y_outputs = list()
for i in xrange(1):
    _X_batch, _y_batch = sess.run([X_batch, y_batch])
    onehot_labels = tf.one_hot(y_batch, 6)
    print('** batch %d' % i)
    print('_X_batch:', _X_batch)
    print('_y_batch:', _y_batch)
    print('onehot_labels', onehot_labels)
    y_outputs.extend(_y_batch.tolist())
print('y_output',y_outputs)

coord.request_stop()
coord.join(threads)