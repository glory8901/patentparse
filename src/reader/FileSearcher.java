package reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import typeobj.FilterFeature;
import utils.file.FilesFilter;
import utils.folder.DeleteDirectory;
import utils.folder.MyFileVisitor;

public class FileSearcher {

	private Map<String, String> typeOutNameMap;
	private Map<String, List<String>> typeOutFilesMap;
	private List<String> extensionList;
	private Map<String, FilterFeature> filterMap;

	public FileSearcher(Element fileSearchArgs) {
		super();
		this.typeOutNameMap = new HashMap<String, String>();// 存放结果
		this.typeOutFilesMap = new HashMap<String, List<String>>();
		this.extensionList = new ArrayList<String>();
		this.filterMap = sepFilter(fileSearchArgs);
		// extends
		Elements extEles = fileSearchArgs.select("extend");
		for (Element ele : extEles) {
			String ext = ele.ownText().trim().toLowerCase();
			this.extensionList.add(ext);
		}
	}

	public List<String> getExtensionList() {
		return extensionList;
	}

	public void setExtensionList(List<String> extensionList) {
		this.extensionList = extensionList;
	}

	public Map<String, FilterFeature> getFilterMap() {
		return filterMap;
	}

	public void setFilterMap(Map<String, FilterFeature> filterMap) {
		this.filterMap = filterMap;
	}

	public Map<String, String> getTypeOutNameMap() {
		return typeOutNameMap;
	}

	public void setTypeOutNameMap(Map<String, String> typeOutNameMap) {
		this.typeOutNameMap = typeOutNameMap;
	}

	public Map<String, List<String>> getTypeOutFilesMap() {
		return typeOutFilesMap;
	}

	public void setTypeOutFilesMap(Map<String, List<String>> typeOutFilesMap) {
		this.typeOutFilesMap = typeOutFilesMap;
	}

	/**
	 * 文件搜索模块 读取配置,搜索文件
	 * 
	 * @param fileSearchArgs
	 * @param base
	 * @return
	 */
	public void search(Element fileSearchArgs, Elements base) {
		// 读取配置文件中搜索文件的条件，并返回搜索结果
		String followpath = fileSearchArgs.select("AddPath").get(0).ownText().trim();
		String country = fileSearchArgs.select("AddPath").get(0).attr("class");

		// 临时文件夹，若不存在创建一个
		String tempdir = base.select("tempdir").get(0).ownText() + fileSearchArgs.select("AddPath").get(0).ownText();
		File tempFile = new File(tempdir);
		if (!tempFile.exists()) {
			tempFile.mkdirs();
		}

		// search dir:如果是相对路径就添加，否则就直接用绝对路径
		String fullPath = "";
		if (followpath.startsWith("\\")) {
			fullPath = base.select("basedir." + country).get(0).ownText() + followpath;// 获取base模块中存储的路径
		} else if ("".equals(followpath)) {
			fullPath = base.select("basedir." + country).get(0).ownText().trim();
		} else {
			fullPath = followpath;
		}
		System.out.println("当前搜索：" + fullPath);

		// 遍历
		try {
			walkFolder(fullPath, tempdir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 使用新的遍历规则较快
	 * 
	 * @param fullPath
	 * @param map
	 * @throws IOException
	 */
	public void walkFolder(String fullPath, String tempdir) throws IOException {
		// 获取文件列表，并保存为中间文件
		getFileList(fullPath, extensionList, tempdir);
	}

	public void filterFileList(List<String> fileList, Map<String, FilterFeature> map) {
		for (String ext : extensionList) {
			FilterFeature ft = map.get(ext);
			typeOutNameMap.put(ext, ft.getOutName());
			typeOutFilesMap.put(ext, new ArrayList<String>());
		}

		// 以后可以采用多个线程来筛选
		for (String filePath : fileList) {
			// 建立文件
			File cFile = new File(filePath);
			// 获取父目录
			String checkdir = cFile.getParentFile().getAbsolutePath();
			String fileName = cFile.getName();
			String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
			// 获取扩展名
			FilterFeature ft = map.get(ext);
			// 筛选文件
			boolean toAdd = filteDirFiles(checkdir, fileName, ft);
			if (toAdd) {
				typeOutFilesMap.get(ext).add(filePath);
			}
			// 清空对象
			cFile = null;
		}

	}

	public boolean filteDirFiles(String checkdir, String checkfileName, FilterFeature ft) {

		// 筛选条件
		String dirContainName = ft.getIncdirName();
		String dirExcludeName = ft.getExcdirName();
		String fileContainName = ft.getIncfileName();
		String fileExcludeName = ft.getExcfileName();

		// 判断文件夹
		if ((dirContainName != null && !"".equals(dirContainName))) {
			boolean shouldRetain = FilesFilter.contains(checkdir, dirContainName);// 或的关系，包含其中一个即可
			if (!shouldRetain) {
				return false;
			}
		}
		if (dirExcludeName != null && !"".equals(dirExcludeName)) {
			// 是否保留与是否删除
			boolean shouldExclude = FilesFilter.exclude(checkdir, dirExcludeName);// 与的关系，出现在列表中的都要排除
			if (shouldExclude) {
				return false;
			}
		}

		// 判断文件
		if ((fileContainName != null && !"".equals(fileContainName))) {
			// 是否保留与是否删除
			boolean shouldRetain = FilesFilter.contains(checkfileName, fileContainName);// 或的关系，包含其中一个即可
			if (!shouldRetain) {
				return false;
			}
		}
		if (fileExcludeName != null && !"".equals(fileExcludeName)) {
			boolean shouldExclude = FilesFilter.exclude(checkfileName, fileExcludeName);// 与的关系，出现在列表中的都要排除
			if (shouldExclude) {
				return false;
			}
		}
		// 最后返回true
		return true;
	}

	public Map<String, FilterFeature> sepFilter(Element fileSearchArgs) {
		Map<String, FilterFeature> filterMap = new HashMap<String, FilterFeature>();

		Elements filterArgs = fileSearchArgs.select("filter");
		for (Element filterArg : filterArgs) {
			// filter: 筛选文件夹的条件
			String incdirs = filterArg.select("incdir").get(0).ownText();
			String excdirs = filterArg.select("excdir").get(0).ownText();
			String incfiles = filterArg.select("incfile").get(0).ownText();
			String excfiles = filterArg.select("excfile").get(0).ownText();
			String ext = filterArg.select("extend").get(0).ownText();
			String outname = filterArg.select("outfile").get(0).ownText();

			FilterFeature ft = new FilterFeature(incdirs, excdirs, incfiles, excfiles, ext, outname);
			filterMap.put(ext, ft);
		}
		return filterMap;
	}

	/**
	 * 遍历文件夹
	 * 
	 * @param basedir
	 * @param extList
	 * @throws IOException
	 */
	public void getFileList(String basedir, List<String> extList, String tempdir) throws IOException {
		if (!new File(basedir).exists()) {
			throw new FileNotFoundException(basedir + " 不存在");
		}
		Path fileDir = Paths.get(basedir);

		// 清空tempdir
		Path directory = Paths.get(tempdir);
		DeleteDirectory walk = new DeleteDirectory();
		EnumSet opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		Files.walkFileTree(directory, opts, Integer.MAX_VALUE, walk);

		// 查找这两个扩展名的文件
		MyFileVisitor visitor = new MyFileVisitor(extList, tempdir);
		// walk
		Files.walkFileTree(fileDir, visitor);
		// 最后的一部分
		visitor.writeLastList();
	}
}
