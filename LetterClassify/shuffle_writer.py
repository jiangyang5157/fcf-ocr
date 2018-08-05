import tensorflow as tf
import numpy as np
#from tqdm import tqdm
import sys
import os
import time
import cv2 as cv2
import random


# png path
TRAINING_DIR = '/Users/Tracy/Documents/FCF/LetterClassify/resizedCapital/'
TESTING_DIR = '/Users/Tracy/Documents/FCF/LetterClassify/validateTest/'
# tfrecord file name
TRAINING_TFRECORD_NAME = 'training.tfrecord'
TESTING_TFRECORD_NAME = 'testing.tfrecord'

DICT_LABEL_TO_ID = { 
    '0': 0, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9, '10': 10, '11': 11, '12': 12, '13': 13, '14': 14,
    '15': 15, '16': 16, '17': 17, '18': 18, '19': 19, '20': 20, '21': 21, '22': 22, '23': 23, '24': 24, '25': 25, '26': 26, '27': 27,'28': 28,
    '29': 29, '30': 30, '31': 31, '32': 32, '33': 33, '34': 34, '35': 35, '36': 36, '37': 37, '38': 38, '39': 39, '40': 40, '41': 41, '42': 42,
    '43': 43, '44': 44, '45': 45, '46': 46, '47': 47, '48': 48, '49': 49, '50': 50, '51': 51, '52': 52, '53': 53, '54': 54, '55': 55, '56': 56,
    '57': 57, '58': 58, '59': 59, '60': 60, '61': 61,
}


def bytes_feature(values):
    return tf.train.Feature(bytes_list=tf.train.BytesList(value=[values]))

def float32_feature(values):
    return tf.train.Feature(float_list = tf.train.FloatList(value=[values]))

def int64_feature(values):
    return tf.train.Feature(int64_list=tf.train.Int64List(value=[values]))


def convert_tfrecord_dataset(dataset_dir, tfrecord_name, tfrecord_path='/Users/Tracy/Documents/FCF/LetterClassify/dataCapital/'):
    
    if not os.path.exists(dataset_dir):
        print('wrong png files')
        exit()
    if not os.path.exists(os.path.dirname(tfrecord_path)):
        os.makedirs(os.path.dirname(tfrecord_path))
    tfrecord_file = os.path.join(tfrecord_path, tfrecord_name)
    class_names = os.listdir(dataset_dir)
    filePaths = []
    for class_name in class_names:  
        class_dir = os.path.join(dataset_dir, class_name)
        file_names = os.listdir(class_dir)
        n_sample = len(file_names)
        for i in range(n_sample):
            file_name = file_names[i]
            png_path = os.path.join(class_dir, file_name)
            filePaths.append(png_path)
        random.shuffle(filePaths)

    n_sample = len(filePaths)
    with tf.python_io.TFRecordWriter(tfrecord_file) as writer:
        for i in range(n_sample):
            png_path = filePaths[i]
            folderPath = os.path.dirname(png_path)
            label=folderPath.split("/")[-1]
            label_id = DICT_LABEL_TO_ID.get(label)
            # CNN inputs using
            img = tf.gfile.FastGFile(png_path, 'rb').read()
            example = tf.train.Example(
                features=tf.train.Features(
                    feature={
                        'image': bytes_feature(img),
                        'label': int64_feature(label_id)
                    }))
            serialized = example.SerializeToString()
            writer.write(serialized)
    print('\nFinished writing data to tfrecord files.')


if __name__ == '__main__':
    convert_tfrecord_dataset(TRAINING_DIR, TRAINING_TFRECORD_NAME)
    #convert_tfrecord_dataset(TESTING_DIR, TESTING_TFRECORD_NAME)