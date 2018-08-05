import math
import os
from argparse import ArgumentParser

import sys
import tensorflow as tf
import numpy as np
import PIL
from PIL import Image
from tensorflow.python.framework import graph_util

IMAGE_SIZE = 784
IMAGE_WIDTH = 28
IMAGE_HEIGHT = 28
IMAGE_CHANNEL_NUM = 1
CONV_1_SIZE = 6
CONV_1_DEPTH = 6
CONV_2_SIZE = 5
CONV_2_DEPTH = 12
CONV_3_SIZE = 4
CONV_3_DEPTH = 24
FC_SIZE = 200

OUTPUT_SIZE = 26

LEARNING_RATE_MAX = 0.003
LEARNING_RATE_MIN = 0.0001
LEARNING_RATE_DECAY_SPEED = 2000
REGULARIZATION_RATE = 0.0001

SUMMARY_INTERVAL = 50
SAVE_INTERVAL = 500
PRINT_INTERVAL = 500

MODEL_NAME = "FCF"

x_image= tf.placeholder(tf.float32, shape=[None, 784])
y_ = tf.placeholder(tf.float32, shape=[None, OUTPUT_SIZE])
x = tf.reshape(x_image, [-1, 28, 28, 1])


def get_weight(shape):
    return tf.get_variable("weight", shape, initializer=tf.truncated_normal_initializer(stddev=0.1))


def get_bias(shape):
    return tf.get_variable("bias", shape, initializer=tf.constant_initializer(0.0))

def conv2d(input_tensor, weight, stride):
    return tf.nn.conv2d(input_tensor, weight, strides=[1, stride, stride, 1], padding="SAME")

def inference(input_tensor, regularizer=None):
    with tf.variable_scope("layer_1_conv"):
        conv_1_w = get_weight([CONV_1_SIZE, CONV_1_SIZE, IMAGE_CHANNEL_NUM, CONV_1_DEPTH])
        conv_1_b = get_bias([CONV_1_DEPTH])
        conv_1 = conv2d(input_tensor, conv_1_w, stride=1)
        conv_1_activation = tf.nn.relu(tf.nn.bias_add(conv_1, conv_1_b))
    with tf.variable_scope("layer_2_conv"):    
        conv_2_w = get_weight([CONV_2_SIZE, CONV_2_SIZE, CONV_1_DEPTH, CONV_2_DEPTH])
        conv_2_b = get_bias([CONV_2_DEPTH])
        conv_2 = conv2d(conv_1_activation, conv_2_w, stride=2)
        conv_2_activation = tf.nn.relu(tf.nn.bias_add(conv_2, conv_2_b))
    with tf.variable_scope("layer_3_conv"):    
        conv_3_w = get_weight([CONV_3_SIZE, CONV_3_SIZE, CONV_2_DEPTH, CONV_3_DEPTH])
        conv_3_b = get_bias([CONV_3_DEPTH])
        conv_3 = conv2d(conv_2_activation, conv_3_w, stride=2)
        conv_3_activation = tf.nn.relu(tf.nn.bias_add(conv_3, conv_3_b))
        
        shape = conv_3_activation.get_shape().as_list()
        nodes = shape[1] * shape[2] * shape[3]
        conv_3_activation_reshaped = tf.reshape(conv_3_activation, [-1, nodes])
    with tf.variable_scope("layer_4_fc"):    
        w4 = get_weight([nodes, FC_SIZE])
        if regularizer is not None:
            tf.add_to_collection("losses", regularizer(w4))
        b4 = get_bias([FC_SIZE])
        a4 = tf.nn.relu(tf.matmul(conv_3_activation_reshaped, w4) + b4)
    with tf.variable_scope("layer_5_fc"):    
        w5 = get_weight([FC_SIZE, OUTPUT_SIZE])
        if regularizer is not None:
            tf.add_to_collection("losses", regularizer(w5))
        b5 = get_bias([OUTPUT_SIZE])
        logits = tf.matmul(a4, w5) + b5
    return logits

def train(options):
    filename_queue = tf.train.string_input_producer(['/Users/Tracy/Documents/FCF/LetterClassify/dataCapital/training.tfrecord'], num_epochs=None, shuffle=True)
    reader = tf.TFRecordReader()
    _, serialized_example = reader.read(filename_queue)
    features = tf.parse_single_example(serialized_example, features={
                                                            'image': tf.FixedLenFeature([], tf.string),
                                                            'label': tf.FixedLenFeature([], tf.int64)
                                                            })
    #input_images = tf.decode_raw(features['image'], tf.float32)
    input_images = features['image']
    input_images = tf.image.decode_png(input_images, channels=1)
    input_images = tf.image.per_image_standardization(input_images)
    input_images = tf.reshape(input_images, [28, 28, 1])
    input_labels = tf.cast(features['label'], tf.int32)
    
    

    #filename_queue = tf.train.string_input_producer(['/Users/Tracy/Documents/FCF/LetterClassify/dataTest/testing.tfrecord'], num_epochs=None, shuffle=True)
    #_, serialized_example = reader.read(filename_queue)
    #features = tf.parse_single_example(serialized_example,
    #                                    features={
    #                                   'image': tf.FixedLenFeature([], tf.string),
    #                                   'label': tf.FixedLenFeature([], tf.int64)
    #                                    })
    #val_images = features['image']
    #val_images = tf.image.decode_png(val_images, channels=1)
    #val_images = tf.reshape(val_images, [1000,28, 28, 1])
    #val_labels = tf.cast(features['label'], tf.int32)
    #val_onehot_labels = tf.one_hot(val_labels, OUTPUT_SIZE)
    

    regularizer = tf.contrib.layers.l2_regularizer(REGULARIZATION_RATE)
    logits = inference(x, regularizer)

    cross_entropy = tf.nn.sparse_softmax_cross_entropy_with_logits(labels=tf.argmax(y_, 1), logits=logits)
    loss = tf.reduce_mean(cross_entropy) + tf.add_n(tf.get_collection("losses"))
    tf.summary.scalar('loss', loss)
        
    global_step = tf.Variable(0, trainable=False)
    decay = tf.train.exponential_decay((LEARNING_RATE_MAX - LEARNING_RATE_MIN), global_step,
                                           LEARNING_RATE_DECAY_SPEED, math.exp(-1), staircase=False)
    learning_rate = LEARNING_RATE_MIN + decay
    train_step = tf.train.AdamOptimizer((learning_rate)).minimize(cross_entropy)

    correct_prediction = tf.equal(tf.argmax(logits, 1), tf.argmax(y_, 1))
    accuracy = tf.reduce_mean(tf.cast(correct_prediction, tf.float32))
    tf.summary.scalar('accuracy', accuracy)

    
    output = tf.nn.softmax(logits, axis=1, name='output')
    merged = tf.summary.merge_all()
    saver = tf.train.Saver()


    batch_size = 100
    iterations = 5000
    
    
    with tf.Session() as sess:
        sess.run(tf.global_variables_initializer())
        batch_image_1, y_batch_1 = tf.train.shuffle_batch([input_images, input_labels], batch_size=batch_size, capacity=300, num_threads=1, min_after_dequeue=100)
    
        coord = tf.train.Coordinator()
        threads = tf.train.start_queue_runners(sess=sess, coord=coord)

        try:
            for it in range(1, iterations+1):
                #batch_image, y_batch = tf.train.shuffle_batch([input_images, input_labels], batch_size=batch_size,
                #                              capacity=200, min_after_dequeue=100, num_threads=3)
            
                batch_image, y_batch = sess.run([batch_image_1, y_batch_1])
                onehot_labels = tf.one_hot(y_batch, OUTPUT_SIZE)
                sess.run(train_step,feed_dict={x: batch_image, y_:onehot_labels.eval()})
            
                if it%50 == 0:
                    print("**** Iteration %d **** summary" %it)
                    t_summary, t_accuracy, t_loss, step = \
                        sess.run([merged, accuracy, loss, global_step], feed_dict={x: batch_image, y_: onehot_labels.eval()})
                    #v_summary, v_accuracy, v_loss = \
                    #    sess.run([merged, accuracy, loss],
                    #             feed_dict={x: val_images.eval(), y_: val_onehot_labels.eval()})
                
                    if it%500 == 0:
                        print("**** Iteration %d ****" %it)
                        print("Train accuracy = %g, loss = %g" % (t_accuracy, t_loss))
                        #print("Validate accuracy = %g, loss = %g" % (v_accuracy, v_loss))
                        saver.save(sess, os.path.join(options.model_dir, MODEL_NAME), global_step=step)
        finally:
            coord.request_stop()
            coord.join(threads)
                                                                                                                                            
        graph_def = tf.get_default_graph().as_graph_def()
        output_graph = graph_util.convert_variables_to_constants(sess, graph_def, ['output'])
        with tf.gfile.GFile(os.path.join(options.model_dir, MODEL_NAME + '.pb'), 'wb') as f:
            f.write(output_graph.SerializeToString())

          
        sess.close()
                                                                                                                   
    print("training complete")

def main():
    parser = build_parser()
    options = parser.parse_args()
    train(options)

def build_parser():
    parser = ArgumentParser()
    parser.add_argument('--model_dir', type=str, dest='model_dir', default='/Users/Tracy/Documents/FCF/LetterClassify/saved_model_capital',
                        help='directory to save checkpoints and model')
    return parser

if __name__ == '__main__':
    main()



