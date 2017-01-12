package eu.koncina.ij.V5S;

import java.io.File;

public class V5sImage {
		public File img = null;
		public V5sPosition targetPosition = new V5sPosition();
		public V5sPosition sourcePosition = new V5sPosition();
		public boolean flipHorizontal = false;
		public boolean flipVertical = false;

		public V5sImage(File f, V5sPosition sourcePosition, V5sPosition targetPosition) {
			this.img = f;
			this.targetPosition = targetPosition;
			this.sourcePosition = sourcePosition;
		}

		public V5sImage(File f, V5sPosition sourcePosition, V5sPosition targetPosition, boolean flipHorizontal, boolean flipVertical) {
			this.img = f;
			this.targetPosition = targetPosition;
			this.sourcePosition = sourcePosition;
			this.flipHorizontal = flipHorizontal;
			this.flipVertical = flipVertical;
		}

		public File getPath() {
			return(this.img);
		}

		public V5sPosition getSourcePosition() {
			return(this.sourcePosition);
		}

		public V5sPosition getTargetPosition() {
			return(this.targetPosition);
		}
		
		public int getTargetChannel() {
			return(this.targetPosition.c);
		}

		public void flipHorizontal() {
			this.flipHorizontal = true;
		}

		public void flipVertical() {
			this.flipVertical = true;
		}
}
