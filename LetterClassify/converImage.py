import os
import PIL
from PIL import Image
from tensorflow.python.framework import graph_util
from argparse import ArgumentParser
import numpy as np

IMAGE_WIDTH = 28
IMAGE_HEIGHT = 28

def main():
    parser = build_parser()
    options = parser.parse_args()
    #for i in range(0, 62):
    #    input_dir = options.input_dir + '/%s'%i
    #    output_dir = options.output_dir + '/%s'%i
    #    imageParser(input_dir, output_dir)
    imageParser(options.input_dir, options.output_dir)


def imageParser(input_dir, output_dir):
    create_if_not_exist(output_dir)
    input_count = 0
    for rt, dirs, files in os.walk(input_dir):
        for filename in files:
            input_count +=1
            im = Image.open(input_dir + '/' + filename)
            out = im.resize((IMAGE_WIDTH,IMAGE_HEIGHT), Image.ANTIALIAS)
    
            im_arr = np.array(out.convert('L'))
            num0 = 0
            num255 = 0
            threshold = 100
                                  
            for x in range(IMAGE_WIDTH):
                for y in range(IMAGE_HEIGHT):
                    if im_arr[x][y] > threshold : num255 = num255+1
                    else : num0 = num0+1
                                  
            if(num255 >num0) :
                print("convert!")
                for x in range(IMAGE_WIDTH):
                    for y in range(IMAGE_HEIGHT):
                        im_arr[x][y] = 255 - im_arr[x][y]
                        if(im_arr[x][y] < threshold) : im_arr[x][y] = 0
            out = Image.fromarray(np.uint8(im_arr))
            out.save(output_dir+ '/' + filename)


def build_parser():
    parser = ArgumentParser()
    parser.add_argument('--input_dir', type=str, dest='input_dir', default='./image',
                        help='directory of original images')
    parser.add_argument('--output_dir', type=str, dest='output_dir', default='./resizedImage',
                        help='directory to save resized images')
        
    return parser

def create_if_not_exist(directory):
    if not os.path.exists(directory):
        os.makedirs(directory)

if __name__ == '__main__':
    main()





