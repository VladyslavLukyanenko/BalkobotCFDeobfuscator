using System.Diagnostics;

namespace BalkoDeobfuscatorTool;

public partial class Form1 : Form
{
  private const string DeobfuscatorJarName = "deobf.jar";

  public Form1()
  {
    InitializeComponent();
  }

  private void _installationDirBtn_Click(object sender, EventArgs e)
  {
    var openFileDialog = new FolderBrowserDialog
    {
      InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
    };

    if (openFileDialog.ShowDialog() != DialogResult.OK)
    {
      return;
    }

    _installDirLbl.Text = openFileDialog.SelectedPath;
    TryEnableStartBtn();
  }

  private void button1_Click(object sender, EventArgs e)
  {
    var openFileDialog = new FolderBrowserDialog
    {
      InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
    };

    if (openFileDialog.ShowDialog() != DialogResult.OK)
    {
      return;
    }

    _outputLbl.Text = openFileDialog.SelectedPath;
    TryEnableStartBtn();
  }

  private async void _startBtn_Click(object sender, EventArgs e)
  {
    _installationDirBtn.Enabled = false;
    _outputBtn.Enabled = false;
    _startBtn.Enabled = false;
    _progressBar.Visible = true;
    try
    {
      await Task.Run(() => Deobfuscate(_installDirLbl.Text, _outputLbl.Text));
      MessageBox.Show("Deobfuscated successfully", "Done");
    }
    catch (Exception exc)
    {
      MessageBox.Show(exc.Message, "Failed to deobfuscate.");
    }
    finally
    {
      _progressBar.Visible = false;
      _installDirLbl.Text = "<Not selected>";
      _outputLbl.Text = "<Not selected>";
      _installationDirBtn.Enabled = true;
      _outputBtn.Enabled = true;
      TryEnableStartBtn();
    }
  }

  private void Deobfuscate(string installDir, string outputDir)
  {
    var jarStartInfo = new ProcessStartInfo
    {
      FileName = "java",
      Arguments = $"-jar \"{DeobfuscatorJarName}\" \"{installDir}\" \"{outputDir}\"",
      UseShellExecute = false,
    };

    Process.Start(jarStartInfo)?.WaitForExit();
    Process.Start("explorer.exe", outputDir);
  }

  private void TryEnableStartBtn()
  {
    _startBtn.Enabled = !string.IsNullOrEmpty(_installDirLbl.Text)
      && !string.IsNullOrEmpty(_outputLbl.Text);
  }
}
