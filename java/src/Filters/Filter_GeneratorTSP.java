package Filters;


import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.text.DecimalFormat;

import Makelangelo.MachineConfiguration;
import Makelangelo.Makelangelo;
import Makelangelo.Point2D;


/**
 * Generate a Gcode file from the BufferedImage supplied.<br>
 * Use the filename given in the constructor as a basis for the gcode filename, but change the extension to .ngc 
 * @author Dan
 */
public class Filter_GeneratorTSP extends Filter {
	public String GetName() { return "Big zig zag"; }
	
	// processing tools
	long t_elapsed,t_start;
	double progress;
	double old_len,len;
	long time_limit=10*60*1000;  // 10 minutes

	int numPoints;
	Point2D[] points = null;
	int[] solution = null;
	int scount;
	

	public String formatTime(long millis) {
    	String elapsed="";
    	long s=millis/1000;
    	long m=s/60;
    	long h=m/60;
    	m%=60;
    	s%=60;
    	if(h>0) elapsed+=h+"h";
    	if(h>0||m>0) elapsed+=m+"m";
    	elapsed+=s+"s ";
    	return elapsed;
	}
	

	public void UpdateProgress(double len,int color) {
		t_elapsed=System.currentTimeMillis()-t_start;
		double new_progress = 100.0 * (double)t_elapsed / (double)time_limit;
		if( new_progress > progress + 0.1 ) {
			// find the new tour length
			len=GetTourLength(solution);
			if( old_len > len ) {
				old_len=len;
				DecimalFormat flen=new DecimalFormat("#.##");
				String c;
				switch(color) {
				case  0: c="yellow";  break;
				case  1: c="blue";    break;
				case  2: c="red";	  break;
				default: c="white";   break;
				}
				Makelangelo.getSingleton().Log("<font color='"+c+"'>"+formatTime(t_elapsed)+": "+flen.format(len)+"mm</font>\n");
			}
			progress = new_progress;
			pm.setProgress((int)progress);
		}
	}
		
/*
	// does moving a point to somewhere else in the series shorten the series?
	// @TODO: Debug, it doesn't seem to work.
	// we have s1,s2,s3...e-1,e
	// check if s1,s3,...e-1,s2,e is shorter
	private int transposeForwardTest() {
		int start, end, j, once=0;
		
		for(start=0;start<numPoints-4 && !isCancelled();++start) {
			float a=CalculateWeight(solution[start],solution[start+1]);  // s1->s2
			float b=CalculateWeight(solution[start+1],solution[start+2]);  // s2->s3
			float d=CalculateWeight(solution[start],solution[start+2]);  // s1->s3
			// a+b > d always true, the line gets shorter at this end
			//assert(a+b>d);
			float abd=a+b-d;

			int best_end=-1;
			double best_diff=0;
			
			for(end=start+4;end<numPoints && !isCancelled();++end) {
				// before
				float c=CalculateWeight(solution[end-1],solution[end]);  // e-1->e
				// after
				float e=CalculateWeight(solution[end-1],solution[start+1]);  // e-1->s2
				float f=CalculateWeight(solution[start+1],solution[end]);  // s2->e
				// e+f > c always true, the line gets longer at this end
				//assert(e+f>c);
				float efc = e+f-c;

				float temp_diff = abd-efc;
				if( best_diff < temp_diff ) {
					best_diff = temp_diff;
					best_end=end;
					//DrawbotGUI.getSingleton().Log("<font color='red'>"+best_diff+"</font>\n");
				}
			}
			
			if(best_end != -1 && !isCancelled()) {
				once = 1;
				// move start+1 to just before end
				int temp=solution[start+1];
				for(j=start+1;j<best_end-1;++j) {
					solution[j]=solution[j+1];
				}
				solution[best_end-1]=temp;

				UpdateProgress(len,0);
			}
		}
		return once;
	}*/
/*
	// does moving a point to somewhere else in the series shorten the series?
	// @TODO: Debug, it doesn't seem to work.
	// we have s1,s2...e-2,e-1,e
	// check if s1,e-1,s2...e-2,e is shorter
	private int transposeBackwardTest() {
		int start, end, j, once=0;
		
		for(start=0;start<numPoints-4 && !isCancelled();++start) {
			float a=CalculateWeight(solution[start],solution[start+1]);  // s1->s2

			int best_end=-1;
			double best_diff=0;
			
			for(end=start+4;end<numPoints && !isCancelled();++end) {
				float b=CalculateWeight(solution[end-2],solution[end-1]);  // e2->e1
				float c=CalculateWeight(solution[end-1],solution[end]);  // e1->e
				float f=CalculateWeight(solution[end-2],solution[end]);  // e2->e
				// b+c > f, this end is getting shorter
				assert(b+c>f);
				float bcf=b+c-f;
				
				float d=CalculateWeight(solution[start],solution[end-1]);  // s1->e1
				float e=CalculateWeight(solution[end-1],solution[start+1]);  // e1->s2
				// d+e>a, this end is getting longer
				assert(d+e>a);
				float dea=d+e-a;

				float temp_diff = bcf-dea;
				if( best_diff < temp_diff ) {
					best_diff = temp_diff;
					best_end=end;
				}
			}
			
			if(best_end != -1 && !isCancelled()) {
				once = 1;
				// move best_end-1 to just after start
				int temp=solution[best_end-1];
				for(j=best_end-1;j>start+1;--j) {
					solution[j]=solution[j-1];
				}
				solution[start+1]=temp;

				UpdateProgress(len,3);
			}
		}
		return once;
	}*/

	// we have s1,s2...e-1,e
	// check if s1,e-1,...s2,e is shorter
	public int flipTests() {
		int start, end, j, once=0;
		
		for(start=0;start<numPoints-2 && !parent.isCancelled() && !pm.isCanceled();++start) {
			float a=CalculateWeight(solution[start],solution[start+1]);
			int best_end=-1;
			double best_diff=0;
			
			for(end=start+2;end<=numPoints && !parent.isCancelled() && !pm.isCanceled();++end) {
				// before
				float b=CalculateWeight(solution[end  ],solution[end  -1]);
				// after
				float c=CalculateWeight(solution[start],solution[end  -1]);
				float d=CalculateWeight(solution[end  ],solution[start+1]);
				
				double temp_diff=(a+b)-(c+d);
				if(best_diff < temp_diff) {
					best_diff = temp_diff;
					best_end=end;
				}
			}
			
			if(best_end != -1 && !parent.isCancelled() && !pm.isCanceled()) {
				once = 1;
				// do the flip
				int begin=start+1;
				int finish=best_end;
				int half=(finish-begin)/2;
				int temp;
				//DrawbotGUI.getSingleton().Log("<font color='red'>flipping "+(finish-begin)+"</font>\n");
				for(j=0;j<half;++j) {
					temp = solution[begin+j];
					solution[begin+j]=solution[finish-1-j];
					solution[finish-1-j]=temp;
				}
				UpdateProgress(len,1);
			}
		}
		return once;
	}
	
	
	protected float CalculateWeight(int a,int b) {
		float x = points[a].x - points[b].x;
		float y = points[a].y - points[b].y;
		return x*x+y*y;
	}
	
	
	private void GenerateTSP() {
		GreedyTour();

		Makelangelo.getSingleton().Log("<font color='green'>Running Lin/Kerighan optimization...</font>\n");

		len=GetTourLength(solution);
		old_len=len;
		
		t_elapsed=0;
		t_start = System.currentTimeMillis();
		progress=0;
		UpdateProgress(len,2);

		int once=1;
		while(once==1 && t_elapsed<time_limit && !parent.isCancelled()) {
			once=0;
			//@TODO: make these optional for the very thorough people
			//once|=transposeForwardTest();
			//once|=transposeBackwardTest();
			once|=flipTests();
			UpdateProgress(len,2);
		}
		
		ConvertAndSaveToGCode();
	}
	
    
	private double CalculateLength(int a,int b) {
		return Math.sqrt(CalculateWeight(a,b));
	}
	
	/**
	 * Get the length of a tour segment
	 * @param list an array of indexes into the point list.  the order forms the tour sequence.
	 * @param start the index of the first point of the tour segment
	 * @param end the index of the last point of the tour segment
	 * @return the length of the tour
	 */
	private double GetTourLength(int[] list) {
		double w=0;
		for(int i=0;i<numPoints-1;++i) {
			w+=CalculateLength(list[i],list[i+1]);
		}
		return w;
	}

	/**
	 * Starting with point 0, find the next nearest point and repeat until all points have been "found".
	 */
	private void GreedyTour() {
		Makelangelo.getSingleton().Log("<font color='green'>Finding greedy tour solution...</font>\n");

		int i;
		float w, bestw;
		int besti;
		
		// put all the points in the solution in no particular order.
		for(i=0;i<numPoints;++i) {
			solution[i]=i;
		}
		

		int scount=0;
		solution[scount]=solution[0];
		
		do {
			// Find the nearest point not already in the line.
			// Any solution[n] where n>scount is not in the line.
			bestw=CalculateWeight(solution[scount],solution[scount+1]);
			besti=scount+1;
			for( i=scount+2; i<numPoints; ++i ) {
				w=CalculateWeight(solution[scount],solution[i]);
				if( w < bestw ) {
					bestw=w;
					besti=i;
				}
			}
			i=solution[scount+1];
			solution[scount+1]=solution[besti];
			solution[besti]=i;
			scount++;
		} while(scount<numPoints);
	}
	

	private void MoveTo(OutputStreamWriter out,int i,boolean up) throws IOException {
		tool.WriteMoveTo(out, points[solution[i]].x, points[solution[i]].y);
	}
	
	
	/**
	 * Open a file and write out the edge list as a set of GCode commands.
	 * Since all the points are connected in a single loop,
	 * start at the tsp point closest to the calibration point and go around until you get back to the start.
	 */
	private void ConvertAndSaveToGCode() {
		// find the tsp point closest to the calibration point
		int i;
		int besti=-1;
		float bestw=1000000;
		float x,y,w;
		for(i=0;i<numPoints;++i) {
			x=points[solution[i]].x;
			y=points[solution[i]].y;
			w=x*x+y*y;
			if(w<bestw) {
				bestw=w;
				besti=i;
			}
		}
		
		// write
		try {
			OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(dest),"UTF-8");
			out.write(MachineConfiguration.getSingleton().GetConfigLine()+";\n");
			out.write(MachineConfiguration.getSingleton().GetBobbinLine()+";\n");

			//MachineConfiguration mc = MachineConfiguration.getSingleton();
			//tool = mc.GetCurrentTool();
			
			SetAbsoluteMode(out);
			tool.WriteChangeTo(out);
			liftPen(out);
			// move to the first point
			MoveTo(out,besti,false);
			lowerPen(out);

			for(i=1;i<numPoints;++i) {
				MoveTo(out,(besti+i)%numPoints,false);
			}
			MoveTo(out,besti,false);
			
			// lift pen and return to home
			liftPen(out);
			SignName(out);
			tool.WriteMoveTo(out,0,0);
			out.close();
		}
		catch(IOException e) {
			Makelangelo.getSingleton().Log("<font color='red'>Error saving "+dest+": "+e.getMessage()+"</font>");
		}
	}
	
	
	protected void ConnectTheDots(BufferedImage img) {
		MachineConfiguration mc = MachineConfiguration.getSingleton();
		tool = mc.GetCurrentTool();
		ImageSetupTransform(img);

		int x,y,i;
		// count the points
		numPoints=0;
		for(y=0;y<image_height;++y) {
			for(x=0;x<image_width;++x) {
				i=decode(img.getRGB(x,y));
				if(i==0) {
					++numPoints;
				}
			}
		}
		
		Makelangelo.getSingleton().Log("<font color='green'>"+numPoints + " points,</font>\n");
		points = new Point2D[numPoints+1];
		solution = new int[numPoints+1];
	
		// collect the point data
		numPoints=0;
		for(y=0;y<image_height;++y) {
			for(x=0;x<image_width;++x) {
				i=decode(img.getRGB(x,y));
				if(i==0) {
					points[numPoints++]=new Point2D(TX(x),TY(y));
				}
			}
		}
	}
	
	/**
	 * The main entry point
	 * @param img the image to convert.
	 */
	public void Convert(BufferedImage img) {
		// resize & flip as needed
		Filter_Resize rs = new Filter_Resize(250,250); 
		img = rs.Process(img);
		// make black & white
		Filter_BlackAndWhite bw = new Filter_BlackAndWhite(255);
		img = bw.Process(img);
		// dither
		Filter_DitherFloydSteinberg dither = new Filter_DitherFloydSteinberg();
		img = dither.Process(img);
		// connect the dots
		ConnectTheDots(img);
		// Shorten the line that connects the dots
		GenerateTSP();
	}
}


/**
 * This file is part of DrawbotGUI.
 *
 * DrawbotGUI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * DrawbotGUI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */