/**
 * Pixelate  
 * by Hernando Barragan.  
 * 
 * Load a QuickTime file and display the video signal 
 * using rectangles as pixels by reading the values stored 
 * in the current video frame pixels array. 
 */

import processing.video.*;
import java.util.*;

int totalChanged; 
//int numPixelsWide, numPixelsHigh;
int blockSize = 10;
Movie movA;
Movie movB;
color movColors[];
int start;
int fadeRate; 
int[] transitionArray; 

void setup() {
  size(640, 360);
  noStroke();
  movA = new Movie(this, "protestwalking.mov");
  movB = new Movie(this, "protestfire.mov"); 
  movB.loop();
  movA.loop();
  movColors = new color[width * height];
  transitionArray = new int[width/blockSize*height/blockSize+2] ;
  Arrays.fill(transitionArray, 0);
  start = frameCount; 
  fadeRate = 10; 
  totalChanged = 0;
}

// Display values from movie
void draw() {
  transitionArray = pixelCrossFade(100, blockSize, start, 300, movColors, transitionArray, movA, movB);
}

public int[] pixelCrossFade(int buffer, int blockSize, int start, int frameDuration, color colorArray[], int[] transArray, Movie mov1, Movie mov2){

  int currentFrame; 
  if(transArray[0] >= frameDuration+buffer){
     Arrays.fill(transArray, 0);
   } else if (transArray[0] < start) {
   } else {
   
   transArray[0] = transArray[0]+1; 
   currentFrame = transArray[0] - start;
   int pixelsHigh = height/blockSize;
   int pixelsWide = width/blockSize;
   int totalPixels = pixelsWide*pixelsHigh;
    println("totalPixels: "+totalPixels);
  int diff; 
  if (currentFrame <= frameDuration/2){
    diff = frameDuration/2-currentFrame;
  } else {
    diff = currentFrame - frameDuration/2;
  }
  
  int changeCount; 
  if (diff <= frameDuration/6){
    changeCount = ceil((0.7*totalPixels)/(frameDuration/3));
  } else if (diff <= frameDuration/3){
    changeCount = ceil((0.1*totalPixels)/(frameDuration/3));
  } else {
    changeCount = ceil((0.05*totalPixels)/(frameDuration/3));
  }
  transArray[1] = transArray[1]+changeCount; 
  
  
  int h = 0; 
  if(transArray[1] <= totalPixels){
    while(h<changeCount) {
    int randomLocation = int(random(2, transArray.length)); 
    if (transArray[randomLocation] != 1){
      transArray[randomLocation] = 1; 
      h++; 
    }
  }
 }
  
  
  if(mov1.available() && mov2.available()){
  mov1.read(); 
  mov2.read(); 
  mov1.loadPixels();
  mov2.loadPixels();
  background(255);
  
  int count = 0; 
  for (int j=0; j<pixelsHigh; j++){
    for(int i=0; i<pixelsWide; i++){
      if(transArray[count+2] == 0){
        colorArray[count] = mov1.get(i*blockSize, j*blockSize);
      } else if (transArray[count+2] == 1){
        colorArray[count] = mov2.get(i*blockSize, j*blockSize);
      }
      count++;
    }
  }
  
  for(int j = 0; j < pixelsHigh; j++){
    for (int i = 0; i<pixelsWide; i++){
      fill(colorArray[j*pixelsWide+i]);
      rect(i*blockSize, j*blockSize, blockSize, blockSize);
    }
  }
}
}
  return transArray; 
  
}

void pixelFade(int fadeRate, int startFrame, int blockSize, color colorArray[], Movie mov){
    int newBlockSize; 
    int pixelsHigh = height / blockSize;
    int pixelsWide = width / blockSize; 
  
   if ((frameCount - startFrame) % fadeRate == 0){
     newBlockSize = blockSize + ((frameCount-startFrame)/fadeRate);
   } else {
     newBlockSize = blockSize + floor((frameCount-startFrame)/fadeRate); 
   }
 
   pixelate(newBlockSize, colorArray, mov);
 
 }

void pixelFocus(int fadeRate, int startFrame, int blockSize, color colorArray[], Movie mov){
  if(blockSize > 0) {
   int newBlockSize; 
   int pixelsHigh = height / blockSize;
   int pixelsWide = width / blockSize;
  
   if ((frameCount - startFrame) % fadeRate == 0){
     newBlockSize = blockSize - ((frameCount-startFrame)/fadeRate);
   } else {
     newBlockSize = blockSize - floor((frameCount-startFrame)/fadeRate); 
   }
   pixelate(newBlockSize, colorArray, mov);
  }
}

void glitchPixelate(int blockSize, color colorArray[], Movie mov){
    mov.read(); 
    mov.loadPixels();
    background(255);
    int pixelsHigh = height / blockSize;
    int pixelsWide = width / blockSize;
    
    //for loop populates color Array with colors in frame
    int count = 0;   
    for (int j=0; j<pixelsHigh; j++){
      for(int i =0; i<pixelsWide; i++) {
        colorArray[count] = mov.get(i*blockSize, j*blockSize);
        count++;
      }
    }
   
    for(int j = 0; j < pixelsHigh; j++){
      for (int i = 0; i<pixelsWide; i++){
        fill(colorArray[j*pixelsWide+i]);
        rect(i*blockSize, j*blockSize, blockSize, blockSize); 
      }
    }
}

//plays 2 movies on top of each other through pixelation -- prioritizes mov1
void crossPixels(int blockSize, color colorArray[], Movie mov1, Movie mov2){
  if(blockSize > 1){
    if(mov1.available() == true && mov2.available() == true){
      mov1.read(); 
      mov2.read();
      mov1.loadPixels();
      mov2.loadPixels();
      background(255);
      int pixelsHigh = height / blockSize;
      int pixelsWide = width / blockSize;
    
    //for loop populates color Array with colors in frames (randomly chooses which mov) 
    int count = 0;   
    int target;  
    for (int j=0; j<pixelsHigh; j++){
      for(int i =0; i<pixelsWide; i++) {
        target = int(random(0,4)); 
        if (target ==0){
          colorArray[count] = mov2.get(i*blockSize, j*blockSize);
        } else {
          colorArray[count] = mov1.get(i*blockSize, j*blockSize);
        }       
        count++;
      }
    }
   
    for(int j = 0; j < pixelsHigh; j++){
      for (int i = 0; i<pixelsWide; i++){
        fill(colorArray[j*pixelsWide+i]);
        rect(i*blockSize, j*blockSize, blockSize, blockSize); 
      }
    }
  }
  }
}


void pixelate(int blockSize, color colorArray[], Movie mov){
  if(blockSize > 1){
  if(mov.available() == true){
    mov.read(); 
    mov.loadPixels();
    background(255);
    int pixelsHigh = height / blockSize;
    int pixelsWide = width / blockSize;
    
    //for loop populates color Array with colors in frame
    int count = 0;   
    for (int j=0; j<pixelsHigh; j++){
      for(int i =0; i<pixelsWide; i++) {
        colorArray[count] = mov.get(i*blockSize, j*blockSize);
        count++;
      }
    }
   
    for(int j = 0; j < pixelsHigh; j++){
      for (int i = 0; i<pixelsWide; i++){
        fill(colorArray[j*pixelsWide+i]);
        rect(i*blockSize, j*blockSize, blockSize, blockSize); 
      }
    }
  }
  }
}
