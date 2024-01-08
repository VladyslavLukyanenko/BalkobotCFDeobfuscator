namespace BalkoDeobfuscatorTool
{
  partial class Form1
  {
    /// <summary>
    ///  Required designer variable.
    /// </summary>
    private System.ComponentModel.IContainer components = null;

    /// <summary>
    ///  Clean up any resources being used.
    /// </summary>
    /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
    protected override void Dispose(bool disposing)
    {
      if (disposing && (components != null))
      {
        components.Dispose();
      }
      base.Dispose(disposing);
    }

    #region Windows Form Designer generated code

    /// <summary>
    ///  Required method for Designer support - do not modify
    ///  the contents of this method with the code editor.
    /// </summary>
    private void InitializeComponent()
    {
      this._installationDirBtn = new System.Windows.Forms.Button();
      this._installDirLbl = new System.Windows.Forms.Label();
      this._outputLbl = new System.Windows.Forms.Label();
      this._outputBtn = new System.Windows.Forms.Button();
      this._startBtn = new System.Windows.Forms.Button();
      this._progressBar = new System.Windows.Forms.ProgressBar();
      this.SuspendLayout();
      // 
      // _installationDirBtn
      // 
      this._installationDirBtn.Location = new System.Drawing.Point(12, 29);
      this._installationDirBtn.Name = "_installationDirBtn";
      this._installationDirBtn.Size = new System.Drawing.Size(564, 78);
      this._installationDirBtn.TabIndex = 0;
      this._installationDirBtn.Text = "Select bot install directory";
      this._installationDirBtn.UseVisualStyleBackColor = true;
      this._installationDirBtn.Click += new System.EventHandler(this._installationDirBtn_Click);
      // 
      // _installDirLbl
      // 
      this._installDirLbl.AutoSize = true;
      this._installDirLbl.Location = new System.Drawing.Point(12, 110);
      this._installDirLbl.Name = "_installDirLbl";
      this._installDirLbl.Size = new System.Drawing.Size(202, 37);
      this._installDirLbl.TabIndex = 1;
      this._installDirLbl.Text = "<Not selected>";
      // 
      // _outputLbl
      // 
      this._outputLbl.AutoSize = true;
      this._outputLbl.Location = new System.Drawing.Point(12, 280);
      this._outputLbl.Name = "_outputLbl";
      this._outputLbl.Size = new System.Drawing.Size(202, 37);
      this._outputLbl.TabIndex = 3;
      this._outputLbl.Text = "<Not selected>";
      // 
      // _outputBtn
      // 
      this._outputBtn.Location = new System.Drawing.Point(12, 199);
      this._outputBtn.Name = "_outputBtn";
      this._outputBtn.Size = new System.Drawing.Size(564, 78);
      this._outputBtn.TabIndex = 2;
      this._outputBtn.Text = "Select results output directory";
      this._outputBtn.UseVisualStyleBackColor = true;
      this._outputBtn.Click += new System.EventHandler(this.button1_Click);
      // 
      // _startBtn
      // 
      this._startBtn.Enabled = false;
      this._startBtn.Location = new System.Drawing.Point(12, 406);
      this._startBtn.Name = "_startBtn";
      this._startBtn.Size = new System.Drawing.Size(564, 83);
      this._startBtn.TabIndex = 4;
      this._startBtn.Text = "Start";
      this._startBtn.UseVisualStyleBackColor = true;
      this._startBtn.Click += new System.EventHandler(this._startBtn_Click);
      // 
      // _progressBar
      // 
      this._progressBar.Location = new System.Drawing.Point(12, 355);
      this._progressBar.Name = "_progressBar";
      this._progressBar.Size = new System.Drawing.Size(564, 45);
      this._progressBar.Style = System.Windows.Forms.ProgressBarStyle.Marquee;
      this._progressBar.TabIndex = 5;
      this._progressBar.Visible = false;
      // 
      // Form1
      // 
      this.AutoScaleDimensions = new System.Drawing.SizeF(15F, 37F);
      this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
      this.ClientSize = new System.Drawing.Size(588, 501);
      this.Controls.Add(this._progressBar);
      this.Controls.Add(this._startBtn);
      this.Controls.Add(this._outputLbl);
      this.Controls.Add(this._outputBtn);
      this.Controls.Add(this._installDirLbl);
      this.Controls.Add(this._installationDirBtn);
      this.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedToolWindow;
      this.Name = "Form1";
      this.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
      this.Text = "Balko deobfuscation tool";
      this.ResumeLayout(false);
      this.PerformLayout();

    }

    #endregion

    private Button _installationDirBtn;
    private Label _installDirLbl;
    private Label _outputLbl;
    private Button _outputBtn;
    private Button _startBtn;
    private ProgressBar _progressBar;
  }
}